package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.AbstractMap;
import java.util.Arrays;

public class User {
    private String myAddress;
    private String username;
    private String password;
    private boolean isSuperuser;
    private Serializer serializer;
    private BufferedReader input;
    private SpreadConnection connection;
    private SpreadGroup superGroup;
    private boolean signedIn;
    private Followee followee;
    private Follower follower;


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
            checkCredentials("SIGN IN");
        }
        else {
            checkCredentials("SIGN UP");
        }
    }

    public void checkCredentials(String type) throws IOException, SpreadException {
        Msg msg = null;
        String result = "NACK";
        System.out.println("\n################## "+type+" ###################");
        while (result.equals("NACK")) {
            // Get login credentials
            System.out.print("> Username: ");
            this.username = this.input.readLine();
            System.out.print("> Password: ");
            this.password = this.input.readLine();
            // Open spread connection
            openSpreadConnection("localhost");
            // Send message to central
            msg = new Msg();
            msg.setType(Arrays.toString(type.split(" ")));
            msg.setPassword(this.password);
            msg.setIp(this.myAddress);
            sendMsg(msg, "centralGroup");
            // Wait for central's reply
            SpreadMessage message = this.connection.receive();
            msg = this.serializer.decode(message.getData());
            result = msg.getType();
        }

        this.signedIn = true;
        this.followee = new Followee(this.username, this.serializer, this.connection);
        this.follower = new Follower(this.username, this.serializer, this.connection);
        handleCentralReply(msg);
    }

    public void handleCentralReply(Msg msg) {

    }

    public void promotion() throws SpreadException {
        this.isSuperuser = true;
        // Connect to superuser's group
        this.superGroup = new SpreadGroup();
        this.superGroup.join(this.connection, this.username+"SuperGroup");
    }

    public void updateSuperuser(String superuserIp, String superuser) throws SpreadException {
        // Leave groups
        this.follower.logout();
        this.followee.logout();
        // Disconnect from my daemon
        this.connection.disconnect();
        // Connect to superuser's daemon
        openSpreadConnection(superuserIp);
        // Connect to superuser's group
        this.superGroup = new SpreadGroup();
        this.superGroup.join(this.connection, superuser+"SuperGroup");

    }


    public void openSpreadConnection (String address) {
        try {
            this.connection = new SpreadConnection();
            this.connection.connect(
                    InetAddress.getByName(address),
                    4803,
                    this.username,
                    false,
                    true);
        } catch (SpreadException e) {
        } catch (UnknownHostException e) {}
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
