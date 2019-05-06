package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

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
    private long startTime;
    private long averageUpTime;
    private long totalSignIns;

    public void setSuperuser(boolean superuser) {
        isSuperuser = superuser;
    }

    public String getMyAddress() {
        return myAddress;
    }

    public boolean isSuperuser() {
        return isSuperuser;
    }

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
                        Msg.class)
                .build();
        this.input = new BufferedReader(new InputStreamReader(System.in));
        this.superGroup = null;
        this.signedIn = false;
        this.prepareSignOut = false;
        this.startTime = System.nanoTime();
        this.averageUpTime = 0; //TODO: Persistência
        this.totalSignIns = 0; //TODO: Persistência

        // Timer that transforms the User into a Super-user after 2 days of current uptime
        // TODO: Persistência
        if (this.averageUpTime >= 1.728e+14) {
            SUTransform t = new SUTransform(this);
            new Timer().schedule(t, this.averageUpTime);
        }

        // Sign in/Sign up
        initialMenu();
    }

    public void initialMenu() throws IOException, SpreadException {
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

        this.followee = new Followee(this.username, this.serializer, this.connection, this.input, this);
        this.follower = new Follower(this.username, this.serializer, this.connection);

        handleCentralReply(msg);

        // Sign in
        this.signedIn = true;
        this.follower.signIn();
        this.followee.signIn();
        this.totalSignIns += 1;

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

        long endTime = System.nanoTime();
        this.averageUpTime = (endTime - this.startTime)/this.totalSignIns;

        //TODO: Persistir as coisas que devem ser persistidas
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

    public void sendMsg(Msg m, String group) throws SpreadException {
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

    public void processMsg(SpreadMessage message) throws SpreadException, InterruptedIOException {
        if (message.isRegular()) {
            processRegularMsg(message);
        }
        else {
            processMembershipMsg(message);
        }
    }

    private void processRegularMsg(SpreadMessage message) throws SpreadException, InterruptedIOException {
        Msg msg = this.serializer.decode(message.getData());
        String followee = message.getMembershipInfo().getGroup().toString().split("Group")[0];
        switch (msg.getType()) {
            // Followee post
            case "POST":
                this.follower.updatePosts(getUsername(message.getSender().toString()), msg.getPosts(), true, "POST");
                break;
            // Follower receives posts
            case "POSTS":
                // Received posts from the followee
                if (followee.equals(getUsername(message.getSender().toString()))) {
                    this.follower.updatePosts(getUsername(message.getSender().toString()), msg.getPosts(), true, "POSTS");
                }
                // Received posts from a follower
                else {
                    this.follower.updatePosts(followee, msg.getPosts(), msg.getStatus(), "POSTS");
                }
                break;
            // Follower requests posts
            case "UPDATE":
                Msg reply = new Msg();
                reply.setType("POSTS");
                // I'm the followee
                if (this.followee.getUsername().equals(followee)) {
                    // Get my posts
                    reply.setPosts(this.followee.getPosts(msg.getLastPostId()));
                    reply.setStatus(true);
                    this.followee.sendMsg(reply, message.getSender().toString());
                }
                // I'm a follower
                else {
                    // Get followee's posts
                    Pair<Boolean, List<Post>> pair = this.follower.getPosts(followee, msg.getLastPostId());
                    reply.setPosts(pair.getSnd());
                    reply.setStatus(pair.getFst());
                    this.follower.sendMsg(reply, message.getSender().toString());
                }
                break;
            // User gets promoted to superuser
            case "PROMOTION":
                promotion();
                // Superuser update
            case "SUPERUSER":
                updateSuperuser(msg.getSuperuserIp(), msg.getSuperuser());
            default:
                break;
        }
    }

    private void processMembershipMsg(SpreadMessage message) throws SpreadException {
        if(! this.prepareSignOut) {
            // Find which followee group originated the message
            String followee = message.getMembershipInfo().getGroup().toString().split("Group")[0];

            if (message.getMembershipInfo().isCausedByJoin()) {
                String joinedUser = getUsername(message.getMembershipInfo().getJoined().toString());

                // Followee signIn
                if (joinedUser.equals(followee)) {
                    // If this followees' posts are outdated, send request
                    if (!this.follower.checkPostsStatus(followee)) {
                        this.follower.sendPostsRequest(message.getMembershipInfo().getJoined().toString());
                    }
                }

                // My signIn
                else if (joinedUser.equals(this.follower.getUsername())) {
                    this.follower.sendPostsRequest(selectMember(message, followee));
                }
            }
        }
        // Super user is preparing to sign out
        else {
            int connectedUsers = message.getMembershipInfo().getMembers().length;

            if(connectedUsers == 1){
                this.superGroup.leave();
                this.signedIn = false;
            }
        }
    }

    public String getUsername (String privateGroup) {
        // Select private name from private group
        String[] parts = privateGroup.substring(1).split("#");
        return parts[0];
    }

    private String selectMember(SpreadMessage message, String followee) {
        List<SpreadGroup> members = Arrays.asList(message.getMembershipInfo().getMembers());
        Map<String, String> usernames = members.stream()
                .collect(Collectors.toMap(m -> getUsername(m.toString()), m -> m.toString()));

        // Followee online
        if (usernames.keySet().contains(followee)) {
            return usernames.get(followee);
        }

        // Select random follower
        Random rand = new Random();
        return members.get(rand.nextInt(members.size())).toString();
    }
}
