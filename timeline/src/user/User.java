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

//TODO: passar os métodos sendMsg, getUsername, etc para uma superclasse

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
    private boolean prepareSignOut;
    private Followee followee;
    private Follower follower;

    public SpreadGroup getSuperGroup() {
        return superGroup;
    }

    public boolean isPrepareSignOut() {
        return prepareSignOut;
    }

    public Serializer getSerializer() {
        return serializer;
    }

    public SpreadConnection getConnection() {
        return connection;
    }

    public boolean isSignedIn() {
        return signedIn;
    }

    public Followee getFollowee() {
        return followee;
    }

    public Follower getFollower() {
        return follower;
    }

    public void setSignedIn(boolean signedIn) {
        this.signedIn = signedIn;
    }

    public User(String myAddress) throws IOException, SpreadException {
        this.myAddress = myAddress;
        this.serializer = Serializer.builder()
                .withTypes(
                        Msg.class,
                        AbstractMap.SimpleEntry.class)
                .build();
        this.input = new BufferedReader(new InputStreamReader(System.in));
        this.superGroup = null;
        this.signedIn = false;
        this.prepareSignOut = false;

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
            // Get signIn credentials
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

        this.followee = new Followee(this.username, this.serializer, this.connection);
        this.follower = new Follower(this.username, this.serializer, this.connection);

        handleCentralReply(msg);

        // Sign in
        this.signedIn = true;
        this.follower.signIn();
        this.followee.signIn();

        // Initialize UserReceiver thread
        UserReceiver ur = new UserReceiver(this);
        Thread t = new Thread(ur);
        t.start();

    }

    public void handleCentralReply(Msg msg) throws SpreadException, InterruptedIOException {
        if(msg.getType().equals("PROMOTION")){
            promotion();
        }
        else {
            updateSuperuser(msg.getSuperuserIp(), msg.getSuperuser());
        }
    }

    public void promotion() throws SpreadException {
        this.isSuperuser = true;
        // Connect to superuser's group
        this.superGroup = new SpreadGroup();
        this.superGroup.join(this.connection, this.username+"SuperGroup");
    }

    public void updateSuperuser(String superuserIp, String superuser) throws SpreadException, InterruptedIOException {
        // Sign out temporarily
        if(this.isSuperuser){
            superuserSignOut();
        }
        else {
            signOut();
        }
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


    public void signOut() throws SpreadException, InterruptedIOException {
        this.signedIn = false;

        // Inform central
        Msg msg = new Msg();
        msg.setType("SIGNOUT");
        sendMsg(msg, "centralGroup");

        // Leave all groups
        this.followee.signOut();
        this.follower.signOut();
        this.superGroup.leave();
    }

    public void superuserSignOut() throws SpreadException, InterruptedIOException {
        // Inform central
        Msg msg = new Msg();
        msg.setType("SIGNOUT");
        sendMsg(msg, "centralGroup");

        // Leave all groups
        this.followee.signOut();
        this.follower.signOut();

        // Wait for all users to leave the super group
        this.prepareSignOut = true;
    }

    private void sendMsg(Msg m, String group) throws SpreadException {
        SpreadMessage message = new SpreadMessage();

        message.setData(this.serializer.encode(m));
        message.addGroup(group);
        message.setAgreed();
        message.setReliable();

        connection.multicast(message);
    }

    //TODO: Falta implementar as condições de transformação e a atualização da conecção do spread.
    private void becomeSuperuser() throws SpreadException {
        SpreadMessage message = new SpreadMessage();

        this.isSuperuser = true;
        Msg msg = new Msg();
        msg.setType("SUPERUSER");
        msg.setSuperuserIp(this.myAddress);

        message.setData(this.serializer.encode(msg));
        message.addGroup("centralGroup");
        message.setAgreed();
        message.setReliable();

        connection.multicast(message);

        this.superGroup.leave();
    }
}
