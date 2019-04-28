package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

public class User {
    private String myAddress;
    private String username;
    private String password;
    private Map<Integer, Post> myPosts;
    private Map<String, Pair<Boolean, Map<Integer, Post>>> followees;
    private boolean isSuperuser;
    private Serializer serializer;
    private BufferedReader input;
    private SpreadConnection connection;
    private SpreadGroup centralGroup;
    private boolean signedIn;


    public User() throws IOException, SpreadException {
        this.serializer = Serializer.builder()
                .withTypes(
                        Msg.class,
                        AbstractMap.SimpleEntry.class)
                .build();
        this.input = new BufferedReader(new InputStreamReader(System.in));
        this.signedIn = false;

        menu();
    }

    public void menu() throws IOException, SpreadException {
        System.out.println("#################### MENU ####################");
        System.out.println("      (1) Sign in      |      (2) Sign up     ");
        String read = this.input.readLine();
        while (!read.equals("1") && !read.equals("2")) {
            System.out.println("Error: Incorrect option. Please try again.");
            read = this.input.readLine();
        }
        if (read.equals("1")) {
            handleOption("SIGN IN");
        }
        else {
            handleOption("SIGN UP");
        }
    }

    public void handleOption (String choice) throws IOException, SpreadException {
        Msg reply = null;

        String result = "NACK";
        System.out.println("\n################## "+choice+" ###################");
        while (result.equals("NACK")) {
            System.out.print("> Username: ");
            this.username = this.input.readLine();
            System.out.print("> Password: ");
            this.password = this.input.readLine();
            // Connect to central and send credentials
            if (openSpreadConnection(centralAddress)) {
                // Join central group
                // TODO: É preciso juntar-se ao grupo para enviar uma mensagem unicast?
                this.centralGroup = new SpreadGroup();
                this.centralGroup.join(this.connection, "centralGroup");
                Msg msg = new Msg();
                //msg.setUsername(this.username);
                msg.setPassword(this.password);
                msg.setType(choice);
                msg.setIp(this.myAddress);
                sendMsg(msg, "#central#"+centralAddress);
                // TODO: Colocar o timeout
                SpreadMessage message = this.connection.receive();
                reply = this.serializer.decode(message.getData());
                result = reply.getType();
            }
            if (result.equals("NACK")) {
                System.out.println("Error: Operation failed. Please try again.");
            }
        }
        this.centralGroup.leave();
        this.signedIn = true;
        openSpreadConnection(reply.getSuperuser());
    }

    public boolean openSpreadConnection (String address) {
        //TODO: o user vai fazer join ao grupo da central antes do login
        // e depois vai fazer leave quando já tiver um superuser ao qual se conectar
        // por isso já não vai precisar da variável centralGroup
        boolean success = true;
        try {
            this.connection = new SpreadConnection();
            this.connection.connect(
                    InetAddress.getByName(address),
                    4803,
                    this.username,
                    false,
                    true);
        } catch (SpreadException e) {
            success = false;
        } catch (UnknownHostException e) {
            success = false;
        }
        return success;
    }


    public void logout() throws SpreadException, InterruptedIOException {
        this.signedIn = false;
        // TODO: É preciso abrir de novo conexão com a central?
        openSpreadConnection(centralAddress);
        Msg msg = new Msg();
        msg.setType("LOGGED_OUT");
        sendMsg(msg, "#central#"+centralAddress);
        SpreadMessage message = this.connection.receive();
        Msg reply = this.serializer.decode(message.getData());
        if (reply.getType().equals("DISCONNECT")) {
            this.connection.disconnect();
            System.out.println("LOGGED OUT!");
        }
    }

    private void sendMsg(Msg m, String group) throws SpreadException {
        SpreadMessage message = new SpreadMessage();

        message.setData(this.serializer.encode(m));
        message.addGroup(group);
        message.setAgreed();
        message.setReliable();

        connection.multicast(message);
    }

    //TODO: quando o user se transforma em superuser tem que avisar a central do seu ip para ela o guardar
}
