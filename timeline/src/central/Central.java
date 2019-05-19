package central;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import user.Post;
import utils.Msg;
import utils.Pair;
import utils.Triple;

import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

public class Central {
    private SpreadConnection connection;
    private Serializer s;
    private String myAddress;
    private Map<String, Pair<Boolean, String>> superusers; // <Username, (Online, IP)>
    private Map<String, Triple<String, Boolean, String>> users; // <Username, (Password, Online, IP)>

    public Central(String myAddress) throws UnknownHostException, SpreadException {
        this.connection = new SpreadConnection();
        this.s = Serializer.builder()
                .withTypes(
                        Msg.class,
                        Post.class,
                        GregorianCalendar.class)
                .build();
        this.myAddress = myAddress;
        this.superusers = new HashMap<>();
        this.users = new HashMap<>();

        connection.connect(InetAddress.getByName(myAddress), 0, "central", false, false);

        SpreadGroup group = new SpreadGroup();
        group.join(connection, "centralGroup");
    }

    public void start() throws SpreadException, InterruptedIOException {
        while(true) {
            SpreadMessage message = connection.receive();

            if (message.isRegular()) {
                Msg msg = this.s.decode(message.getData());

                String username = message.getSender().toString().split("#")[1];

                switch (msg.getType()) {
                    case "SIGNUP":
                        signup(message, msg);
                        break;

                    case "SIGNIN":
                        String password = msg.getPassword();
                        String ip = msg.getIp();

                        login(message, username, password, ip);
                        break;
                    case "SIGNOUT":
                        if(this.superusers.containsKey(username)) {
                            System.out.println(username + " logging out as SU");
                            logoutFromSuperuser(username);
                        } else {
                            System.out.println(username + " logging out as U");
                            logoutFromUser(message, username);
                        }
                        break;

                    case "SUPERUSER":
                        // TODO: Avisar todos os superusers da existencia de um novo superuser para eles atualizarem o ficheiro de config
                        String superuserIp = msg.getSuperuserIp();
                        this.superusers.put(username, new Pair(true, superuserIp));
                        break;

                    default:
                        throw new IllegalStateException("Unexpected value: " + msg.getType());
                }
            }
        }
    }

    private void superuserAtribution(SpreadMessage message, String ip) throws SpreadException {
        Msg msg;
        SpreadGroup dest;

        // Caso existam superusers online
        if (this.superusers.values().stream().anyMatch(p -> p.getFst() == true)) {
            msg = new Msg();
            msg.setType("SUPERUSER");

            String superuser = getRandomSuperuser();
            String superuserIp = this.superusers.get(superuser).getSnd();

            msg.setSuperuser(superuser);
            msg.setSuperuserIp(superuserIp);
            dest = message.getSender();
            sendMsg(msg, dest);
        } else { // Caso contrário
            String username = message.getSender().toString().split("#")[1];

            this.superusers.put(username, new Pair<>(true, ip));

            msg = new Msg();
            msg.setType("PROMOTION");
            msg.setSuperuser(username);
            dest = message.getSender();
            sendMsg(msg, dest);
        }
    }

    private void sendNack(SpreadMessage message) throws SpreadException {
        Msg msg = new Msg();
        msg.setType("NACK");
        SpreadGroup dest = message.getSender();
        sendMsg(msg, dest);
    }

    private void signup(SpreadMessage message, Msg msg) throws SpreadException {
        String username = message.getSender().toString().split("#")[1];
        String password = msg.getPassword();
        String ip = msg.getIp();

        if (this.users.containsKey(username)) {
            sendNack(message);
        } else {
            this.users.put(username, new Triple(password, false, ip));

            login(message, username, password, ip);
        }
    }

    private void login(SpreadMessage message, String username, String password, String ip) throws SpreadException {
        if (this.users.containsKey(username) && password.equals(this.users.get(username).getFst())) {

            // Atualizar as estruturas caso seja User/Superuser
            if (this.superusers.containsKey(username)) {
                this.superusers.put(username, new Pair(true, ip));
            } else {
                this.users.put(username, new Triple(password, true, ip));
            }
            superuserAtribution(message, ip);
            System.out.println(username + " signed in!");
        } else {
            sendNack(message);
        }
    }

    private void logoutFromUser(SpreadMessage message, String username) {
        this.users.put(username, new Triple(this.users.get(username).getFst(), false, ""));
    }

    private void logoutFromSuperuser(String username) throws SpreadException {
        Msg msg = new Msg();
        this.superusers.put(username, new Pair(false, ""));
        this.users.put(username, new Triple(this.users.get(username).getFst(), false, ""));

        if (this.superusers.values().stream().anyMatch(p -> p.getFst() == true)) {
            String superuser = getRandomSuperuser();
            String superuserIp = this.superusers.get(superuser).getSnd();
            msg.setType("SUPERUSER");
            msg.setSuperuser(superuser);
            msg.setSuperuserIp(superuserIp);

            sendMsg(msg, username + "SuperGroup");
        } else {
            msg.setType("PROMOTION");
            String user = getRandomUser();
            if (!"".equals(user)) {
                String userIp = this.users.get(user).getTrd();
                this.superusers.put(user, new Pair<>(true, userIp));

                msg.setSuperuser(user);
                sendMsg(msg, user+"Group");

                System.out.println("Promoted " + user + "!");

                msg.setType("SUPERUSER");
                msg.setSuperuser(user);
                msg.setSuperuserIp(userIp);
                sendMsg(msg, username + "SuperGroup");
            } else {
                System.out.println("No users to promote...");
            }
        }
    }

    private void sendMsg(Msg msg, String group) throws SpreadException {
        SpreadMessage newMessage = new SpreadMessage();
        newMessage.setData(this.s.encode(msg));
        newMessage.addGroup(group);
        newMessage.setAgreed();
        newMessage.setReliable();
        this.connection.multicast(newMessage);
    }

    private String getRandomSuperuser() {
        Random rand = new Random();
        List<String> online = this.superusers.entrySet().stream()
                .filter(e -> e.getValue().getFst())
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        int randomIndex = rand.nextInt(online.size());
        return online.get(randomIndex);
    }

    private String getRandomUser() {
        Random rand = new Random();
        List<String> online = this.users.entrySet().stream()
                .filter(e -> e.getValue().getSnd())
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        int randomIndex;
        if (online.size() > 0) {
            randomIndex = rand.nextInt(online.size());
            return online.get(randomIndex);
        }
        return "";
    }

    private void sendMsg(Msg msg, SpreadGroup dest) throws SpreadException {
        SpreadMessage newMessage = new SpreadMessage();
        newMessage.setData(this.s.encode(msg));
        newMessage.addGroup(dest);
        newMessage.setAgreed();
        newMessage.setReliable();
        this.connection.multicast(newMessage);
    }
}
