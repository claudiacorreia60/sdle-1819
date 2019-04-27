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
    private Map<String, Triple<String, String, Boolean>> users; // <Username, (Password, Online, IP)>

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
                String password;
                String ip;
                String superuser;
                String superuserIp;
                Msg msg2;
                SpreadGroup dest;

                switch (msg.getType()) {
                    case "SIGNUP":
                        signup(message, msg);
                        break;

                    case "LOGIN":
                        password = msg.getPassword();
                        ip = msg.getIp();
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
                        break;
                    case "LOGGED_OUT":
                        if(this.superusers.containsKey(username)) {
                            logoutFromSuperuser(message, username);
                        } else {
                            logoutFromUser(message, username);
                        }
                        break;

                    case "SUPERUSER":
                        superuserIp = msg.getSuperuserIp();
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
            msg2 = new Msg("SUPERUSER");

            String superuser = getRandomSuperuser();
            String superuserIp = this.superusers.get(superuser).getSnd();

            msg2.setSuperuser(superuser);
            msg2.setSuperuserIp(superuserIp);
            dest = message.getSender();
            sendMsg(msg2, dest);
        } else { // Caso contrário
            msg2 = new Msg("PROMOTION");
            dest = message.getSender();
            sendMsg(msg2, dest);
        }
    }

    private void sendAck(SpreadMessage message) throws SpreadException {
        Msg msg2 = new Msg("ACK");
        SpreadGroup dest = message.getSender();
        sendMsg(msg2, dest);
    }

    private void sendNack(SpreadMessage message) throws SpreadException {
        Msg msg2 = new Msg("NACK");
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
            this.users.put(username, new Triple(password, ip, false));
            sendAck(message);
        }
    }

    private void logoutFromUser(SpreadMessage message, String username) throws SpreadException {
        this.users.put(username, new Triple(this.users.get(username).getFst(), "", false));

        Msg msg2 = new Msg("DISCONNECT");
        sendMsg(msg2, message.getSender());
    }

    private void logoutFromSuperuser(SpreadMessage message, String username) throws SpreadException {
        Msg msg2 = new Msg("SUPERUSER_UPDATE");
        this.superusers.put(username, new Pair(false, ""));
        this.users.put(username, new Triple(this.users.get(username).getFst(), "", false));
        String superuser = getRandomSuperuser();
        String superuserIp = this.superusers.get(superuser).getSnd();
        msg2.setSuperuser(superuser);
        msg2.setSuperuserIp(superuserIp);

        sendMsg(msg2,superuser+"SuperGroup");

        msg2.setType("DISCONNECT");
        sendMsg(msg2, message.getSender());
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
                .filter(e -> !e.getValue().getTrd())
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
