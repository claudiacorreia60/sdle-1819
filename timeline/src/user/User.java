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
    private int connectedUsers;
    private long startTime;
    private long averageUpTime;
    private long totalSignIns;

    public User(String myAddress) throws IOException, SpreadException {
        this.myAddress = myAddress;
        this.serializer = Serializer.builder()
                .withTypes(
                        Msg.class,
                        Post.class,
                        GregorianCalendar.class)
                .build();
        this.input = new BufferedReader(new InputStreamReader(System.in));
        this.superGroup = null;
        this.signedIn = false;
        this.prepareSignOut = false;
        this.startTime = System.nanoTime();
        this.averageUpTime = 0; //TODO: Persistência
        this.totalSignIns = 1; //TODO: Persistência
        this.connectedUsers = 0;
        this.connection = new SpreadConnection();

        // Open spread connection
        openSpreadConnection("localhost");

        // Timer that transforms the User into a Super-user after 2 days of current uptime
        // TODO: Persistência
        if (this.averageUpTime == 0) {
            SUTransform t = new SUTransform(this);
            new Timer().schedule(t, 172800*1000);
        } else if (this.averageUpTime >= 1.728e+14) {
            this.isSuperuser = true;
        }

        // Sign in/Sign up
        initialMenu();
    }

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


    public void initialMenu() throws IOException, SpreadException {
        System.out.println("#################### MENU ####################");
        System.out.println("#                (1) Sign in                 #");
        System.out.println("#                (2) Sign up                 #");
        System.out.println("##############################################");
        String read = this.input.readLine();
        String[] options = {"1","2"};
        while (! Arrays.asList(options).contains(read)) {
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
            // Send message to central
            msg = new Msg();
            String[] tokens = type.split(" ");
            msg.setType(tokens[0] + tokens[1]);
            msg.setPassword(this.password);
            msg.setIp(this.myAddress);
            sendMsg(msg, "centralGroup");
            // Wait for central's reply
            SpreadMessage message = this.connection.receive();
            msg = this.serializer.decode(message.getData());
            result = msg.getType();
        }

        this.follower = new Follower(this.username, this.serializer, this.connection, this.input);
        this.followee = new Followee(this.username, this.serializer, this.connection, this.input, this);

        handleCentralReply(msg);

        // Sign in
        this.signedIn = true;
        this.totalSignIns += 1;
        this.follower.signIn();
        this.followee.signIn();

        // Initialize UserReceiver thread
        UserReceiver ur = new UserReceiver(this);
        Thread t = new Thread(ur);
        t.start();

        // Make posts/Logout menu
        timelineMenu();
    }

    public void timelineMenu() throws IOException, SpreadException {
        while (this.signedIn) {
            System.out.println("\n#################### MENU ####################");
            System.out.println("#                  (1) Post                  #");
            System.out.println("#                 (2) Follow                 #");
            System.out.println("#                (3) Unfollow                #");
            System.out.println("#                (4) Sign out                #");
            System.out.println("##############################################");
            String read = this.input.readLine();
            String[] options = {"1","2","3","4"};
            while (! Arrays.asList(options).contains(read)) {
                System.out.println("Error: Incorrect option. Please try again.");
                read = this.input.readLine();
            }
            switch (read) {
                case "1":
                    this.followee.post();
                    break;
                case "2":
                    this.follower.follow();
                    break;
                case "3":
                    this.follower.unfollow();
                    break;
                case "4":
                    if (this.isSuperuser) {
                        superuserSignOut();
                    } else {
                        signOut(true);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void handleCentralReply(Msg msg) throws SpreadException, InterruptedIOException {
        if(msg.getType().equals("PROMOTION")){
            System.out.println("Central reply");
            promotion();
        }
        else {
            updateSuperuser(msg.getSuperuserIp(), msg.getSuperuser());
        }
    }

    public void promotion() throws SpreadException {
        // TODO: mudar o ficheiro de configuração
        // Disconnect from my daemon
        this.connection.disconnect();
        openSpreadConnection(this.myAddress);
        this.isSuperuser = true;
        // Connect to superuser's group
        this.superGroup = new SpreadGroup();
        this.superGroup.join(this.connection, this.username+"SuperGroup");
    }

    public void updateSuperuser(String superuserIp, String superuser) throws SpreadException, InterruptedIOException {
        // Leave groups
        disconnect();
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
            this.connection.connect(
                    InetAddress.getByName(address),
                    4803,
                    this.username,
                    false,
                    true);
        } catch (SpreadException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }


    public void signOut(boolean isExiting) throws SpreadException, InterruptedIOException {
        this.signedIn = false;

        // Inform central
        Msg msg = new Msg();
        msg.setType("SIGNOUT");
        sendMsg(msg, "centralGroup");

        // Leave groups
        disconnect();

        long endTime = System.nanoTime();
        this.averageUpTime = (endTime - this.startTime)/this.totalSignIns;

        if (isExiting) {
            System.exit(0);
        }

        //TODO: Persistir as coisas que devem ser persistidas
    }

    public void disconnect() throws SpreadException, InterruptedIOException {
        // Leave all groups
        this.followee.signOut();
        this.follower.signOut();
        if (this.superGroup != null) {
            this.superGroup.leave();
        }
    }

    public void superuserSignOut() throws SpreadException, InterruptedIOException {
        // Inform central
        Msg msg = new Msg();
        msg.setType("SIGNOUT");
        sendMsg(msg, "centralGroup");

        // Leave all groups
        this.followee.signOut();
        this.follower.signOut();

        if(this.connectedUsers == 1) {
            if (this.superGroup != null) {
                this.superGroup.leave();
            }
            this.signedIn = false;

            System.exit(0);
        } else {
            // Wait for all users to leave the super group
            this.prepareSignOut = true;
        }
    }

    public void sendMsg(Msg m, String group) throws SpreadException {
        SpreadMessage message = new SpreadMessage();

        message.setData(this.serializer.encode(m));
        message.addGroup(group);
        message.setAgreed();
        message.setReliable();

        connection.multicast(message);
    }

    public void becomeSuperuser() throws SpreadException {
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
        this.superGroup.join(this.connection, this.username + "SuperGroup");
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
        String followee = msg.getFollowee();

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
                if (this.username.equals(followee)) {
                    // Get my posts
                    reply.setPosts(this.followee.getPosts(msg.getLastPostId()));
                    reply.setStatus(true);
                    reply.setFollowee(followee);
                    this.followee.sendMsg(reply, message.getSender().toString());
                }
                // I'm a follower
                else {
                    // Get followee's posts
                    Pair<Boolean, List<Post>> pair = this.follower.getPosts(followee, msg.getLastPostId());
                    reply.setPosts(pair.getSnd());
                    reply.setStatus(pair.getFst());
                    reply.setFollowee(followee);
                    this.follower.sendMsg(reply, message.getSender().toString());
                }
                break;
            // User gets promoted to superuser
            case "PROMOTION":
                System.out.println("PROMOTION: " + msg.getSuperuserIp() + " " + msg.getSuperuser());
                promotion();
                // Superuser update
            case "SUPERUSER":
                updateSuperuser(msg.getSuperuserIp(), msg.getSuperuser());
            default:
                break;
        }
    }

    private void processMembershipMsg(SpreadMessage message) throws SpreadException {
        boolean isSuperGroup = message.getMembershipInfo().getGroup().toString().contains("SuperGroup");

        if (! this.prepareSignOut && ! isSuperGroup) {
            processGroupMsg(message);
        }
        // Super user is preparing to sign out
        if (isSuperGroup){
            processSuperGroupMsg(message);
        }
    }

    private void processSuperGroupMsg(SpreadMessage message) throws SpreadException {
        this.connectedUsers = message.getMembershipInfo().getMembers().length;

        if (this.isPrepareSignOut() && this.connectedUsers == 1) {
            this.superGroup.leave();
            this.signedIn = false;

            System.exit(0);
        }
    }

    private void processGroupMsg(SpreadMessage message) throws SpreadException {
        // Find which followee group originated the message
        String followee = message.getMembershipInfo().getGroup().toString().split("Group")[0];

        // Followee doesn't request posts
        if (!followee.equals(this.username)) {
            if (message.getMembershipInfo().isCausedByJoin()) {
                String joinedUser = getUsername(message.getMembershipInfo().getJoined().toString());

                // Followee signIn
                if (joinedUser.equals(followee)) {
                    // If this followees' posts are outdated, send request
                    if (!this.follower.checkPostsStatus(followee)) {
                        this.follower.sendPostsRequest(message.getMembershipInfo().getJoined().toString(), followee);
                    }
                }

                // My signIn
                else if (joinedUser.equals(this.username)) {
                    this.follower.sendPostsRequest(selectMember(message, followee), followee);
                }
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
