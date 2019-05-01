package central;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;
import utils.Triple;

import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class Central {
    private SpreadConnection connection;
    private Serializer s;
    private String myAddress;
    private Map<String, Pair<Boolean, String>> superusers; // <Username, (Online, IP)>
    private Map<String, Triple<String, Boolean, String>> users; // <Username, (Password, Online, IP)>
    private Pair<Boolean,String> waitingPromotion; // (Promoted, LoggedOutSuperuser)

    public Central(String myAddress) throws UnknownHostException, SpreadException {
        this.connection = new SpreadConnection();
        this.s = Serializer.builder()
                .withTypes(
                        Msg.class,
                        Pair.class)
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
                            logoutFromSuperuser(username);
                        } else {
                            logoutFromUser(message, username);
                        }
                        break;

                    case "SUPERUSER":
                        if (this.waitingPromotion != null && this.waitingPromotion.getFst()) {
                            String loggedOutSuperuser = this.waitingPromotion.getSnd();

                            Msg msg2 = new Msg();
                            msg2.setType("SUPERUSER_UPDATE");
                            msg2.setSuperuser(username);
                            msg2.setSuperuserIp(msg.getSuperuserIp());
                            sendMsg(msg2, loggedOutSuperuser+"SuperGroup");

                            msg2.setType("DISCONNECT");
                            sendMsg(msg2, this.superusers.get(loggedOutSuperuser).getSnd());
                            this.waitingPromotion = null;
                        }
                        String superuserIp = msg.getSuperuserIp();
                        this.superusers.put(username, new Pair(true, superuserIp));
                        break;

                    default:
                        throw new IllegalStateException("Unexpected value: " + msg.getType());
                }
            }
        }
    }

    private void superuserAtribution(SpreadMessage message) throws SpreadException {
        Msg msg2;
        SpreadGroup dest;

        // Caso existam superusers online
        if (this.superusers.values().stream().anyMatch(p -> p.getFst() == true)) {
            msg2 = new Msg();
            msg2.setType("SUPERUSER");

            String superuser = getRandomSuperuser();
            String superuserIp = this.superusers.get(superuser).getSnd();

            msg2.setSuperuser(superuser);
            msg2.setSuperuserIp(superuserIp);
            dest = message.getSender();
            sendMsg(msg2, dest);
        } else { // Caso contrário
            msg2 = new Msg();
            msg2.setType("PROMOTION");
            dest = message.getSender();
            sendMsg(msg2, dest);
        }
    }

    private void sendNack(SpreadMessage message) throws SpreadException {
        Msg msg2 = new Msg();
        msg2.setType("NACK");
        SpreadGroup dest = message.getSender();
        sendMsg(msg2, dest);
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
            superuserAtribution(message);
        } else {
            sendNack(message);
        }
    }

    private void logoutFromUser(SpreadMessage message, String username) {
        this.users.put(username, new Triple(this.users.get(username).getFst(), false, ""));
    }

    private void logoutFromSuperuser(String username) throws SpreadException {
        Msg msg2 = new Msg();
        msg2.setType("SUPERUSER_UPDATE");
        this.superusers.put(username, new Pair(false, ""));
        this.users.put(username, new Triple(this.users.get(username).getFst(), false, ""));

        if (this.superusers.values().stream().anyMatch(p -> p.getFst() == true)) {
            String superuser = getRandomSuperuser();
            String superuserIp = this.superusers.get(superuser).getSnd();
            msg2.setSuperuser(superuser);
            msg2.setSuperuserIp(superuserIp);

            sendMsg(msg2, superuser + "SuperGroup");
        } else {
            msg2.setType("PROMOTION");
            String user = getRandomUser();
            sendMsg(msg2, user+"Group");

            this.waitingPromotion = new Pair(true, username);

            String userIp = this.users.get(user).getTrd();
            msg2.setSuperuser(user);
            msg2.setSuperuserIp(userIp);
        }

    }

    private void sendMsg(Msg msg2, String group) throws SpreadException {
        SpreadMessage newMessage = new SpreadMessage();
        newMessage.setData(this.s.encode(msg2));
        newMessage.addGroup(group);
        newMessage.setAgreed();
        newMessage.setReliable();
        this.connection.multicast(newMessage);
    }

    private String getRandomSuperuser() {
        Random rand = new Random();
        List<String> online = this.superusers.entrySet().stream()
                .filter(e -> !e.getValue().getFst())
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        int randomIndex = rand.nextInt(online.size());
        return online.get(randomIndex);
    }

    private String getRandomUser() {
        Random rand = new Random();
        List<String> online = this.users.entrySet().stream()
                .filter(e -> !e.getValue().getSnd())
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        int randomIndex = rand.nextInt(online.size());
        return online.get(randomIndex);
    }

    private void sendMsg(Msg msg2, SpreadGroup dest) throws SpreadException {
        SpreadMessage newMessage = new SpreadMessage();
        newMessage.setData(this.s.encode(msg2));
        newMessage.addGroup(dest);
        newMessage.setAgreed();
        newMessage.setReliable();
        this.connection.multicast(newMessage);
    }
}
