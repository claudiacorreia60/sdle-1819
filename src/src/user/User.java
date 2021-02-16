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
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

public class User implements Serializable {
    private transient String myAddress;
    private transient String username;
    private transient String password;
    private boolean isSuperuser;
    private transient Serializer serializer;
    private transient BufferedReader input;
    private transient SpreadConnection connection;
    private transient SpreadGroup superGroup;
    private transient boolean signedIn;
    private transient boolean prepareSignOut;
    private transient Followee followee;
    private transient Follower follower;
    private transient int connectedUsers;
    private transient long startTime;
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
        this.averageUpTime = 0;
        this.totalSignIns = 1;
        this.connectedUsers = 0;
        this.connection = new SpreadConnection();

        // Timer that transforms the User into a Super-user after 2 days of current uptime
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
            // Open spread connection
            openSpreadConnection("localhost");
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

        String followerFile = this.username + "-follower.db";
        String followeeFile = this.username + "-followee.db";
        String userFile     = this.username + ".db";

        // Follower Deserialization
        followerDeserialization(followerFile);

        // Followee Deserialization
        followeeDeserialization(followeeFile);

        // User Deserialization
        userDeserialization(userFile);

        handleCentralReply(msg);

        // Sign in
        this.signedIn = true;
        this.totalSignIns += 1;

        // Initialize UserReceiver thread
        UserReceiver ur = new UserReceiver(this);
        Thread t = new Thread(ur);
        t.start();

        // Make posts/Logout menu
        timelineMenu();
    }

    private void userDeserialization(String userFile) throws IOException, SpreadException {
        try
        {
            // Reading the object from a file
            FileInputStream file = new FileInputStream(userFile);
            ObjectInputStream in = new ObjectInputStream(file);

            // Method for deserialization of object
            User savedUser = (User) in.readObject();
            this.averageUpTime = savedUser.averageUpTime;
            this.totalSignIns = savedUser.totalSignIns;

            in.close();
            file.close();

            System.out.println("User info has been loaded");
        } catch(Exception ex) {
            System.out.println("Couldn't load User! Using default settings...");
        }
    }

    private void followeeDeserialization(String followeeFile) throws IOException, SpreadException {
        this.followee = new Followee(this.username, this.serializer, this.connection, this.input, this);

        try
        {
            // Reading the object from a file
            FileInputStream file = new FileInputStream(followeeFile);
            ObjectInputStream in = new ObjectInputStream(file);

            // Method for deserialization of object
            Followee savedFollowee = (Followee) in.readObject();
            this.followee.setMyPosts(savedFollowee.getMyPosts());

            in.close();
            file.close();

            System.out.println("Followee info has been loaded");
        } catch(Exception ex) {
            System.out.println("Couldn't load Followee! Using default settings...");
        }
    }

    private void followerDeserialization(String followerFile) {
        this.follower = new Follower(this.username, this.serializer, this.connection, this.input);

        try
        {
            // Reading the object from a file
            FileInputStream file = new FileInputStream(followerFile);
            ObjectInputStream in = new ObjectInputStream(file);

            // Method for deserialization of object
            Follower savedFollower = (Follower) in.readObject();
            this.follower.setFollowees(savedFollower.getFollowees());

            in.close();
            file.close();

            System.out.println("Follower info has been loaded");
        } catch(Exception ex) {
            System.out.println("Couldn't load Follower! Using default settings...");
        }
    }

    public void timelineMenu() throws IOException, SpreadException {
        while (this.signedIn && ! this.prepareSignOut) {
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

    public void handleCentralReply(Msg msg) throws SpreadException, IOException {
        if(msg.getType().equals("PROMOTION")){
            promotion();
        }
        else {
            updateSuperuser(msg.getSuperuserIp(), msg.getSuperuser());
        }
    }

    public void promotion() throws SpreadException, IOException {
        // TODO: mudar o ficheiro de configuração
        // Disconnect from my daemon
        this.connection.disconnect();
        openSpreadConnection(this.myAddress);

        this.isSuperuser = true;

        // Connect to superuser's group
        this.superGroup = new SpreadGroup();
        this.superGroup.join(this.connection, this.username+"SuperGroup");
        // Join groups
        if (! this.signedIn) {
            this.follower.signIn();
            this.followee.signIn();
        }
    }

    public void updateSuperuser(String superuserIp, String superuser) throws SpreadException, IOException {
        System.out.println("Joining " + superuser + " SU Group");
        // Leave groups
        disconnect();
        // Disconnect from my daemon
        this.connection.disconnect();
        // Connect to superuser's daemon
        openSpreadConnection(superuserIp);
        // Connect to superuser's group
        this.superGroup = new SpreadGroup();
        this.superGroup.join(this.connection, superuser+"SuperGroup");
        // Join groups
        this.follower.signIn();
        this.followee.signIn();
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


    public void signOut(boolean isExiting) throws SpreadException, IOException {
        this.signedIn = false;

        // Inform central
        Msg msg = new Msg();
        msg.setType("SIGNOUT");
        sendMsg(msg, "centralGroup");

        // Leave groups
        disconnect();

        long endTime = System.nanoTime();
        this.averageUpTime = (this.averageUpTime * (totalSignIns-1) + 
                             (endTime - this.startTime))/this.totalSignIns;

        if (isExiting) {
            String filename = this.username +".db";

            // Serialization
            userSerialization(filename);

            System.exit(0);
        }

    }

    private void userSerialization(String filename) throws IOException {
        String followerFile = this.username + "-follower.db";
        String followeeFile = this.username + "-followee.db";

        //Saving user in a file
        FileOutputStream file = new FileOutputStream(filename);
        ObjectOutputStream out = new ObjectOutputStream(file);

        // Method for serialization of object
        out.writeObject(this);

        // Saving follower in a file
        file = new FileOutputStream(followerFile);
        out = new ObjectOutputStream(file);
        out.writeObject(this.follower);

        // Saving followee in a file
        file = new FileOutputStream(followeeFile);
        out = new ObjectOutputStream(file);
        out.writeObject(this.followee);

        out.close();
        file.close();

        System.out.println("Information has been saved!");
    }

    public void disconnect() throws SpreadException, InterruptedIOException {
        // Leave all groups
        this.followee.signOut();
        this.follower.signOut();
        if (this.superGroup != null) {
            this.superGroup.leave();
        }
    }

    public void superuserSignOut() throws SpreadException, IOException {
        System.out.println("Logging out as SU");
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

            long endTime = System.nanoTime();
            this.averageUpTime = (this.averageUpTime * (totalSignIns-1) +
                    (endTime - this.startTime))/this.totalSignIns;

            String filename = this.username +".db";

            // Serialization
            userSerialization(filename);

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
        message.setCausal();
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
        message.setCausal();
        message.setReliable();

        connection.multicast(message);

        this.superGroup.leave();
        this.superGroup.join(this.connection, this.username + "SuperGroup");
    }

    public void processMsg(SpreadMessage message) throws SpreadException, IOException {
        if (message.isRegular()) {
            processRegularMsg(message);
        }
        else {
            processMembershipMsg(message);
        }
    }

    private void processRegularMsg(SpreadMessage message) throws SpreadException, IOException {
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
                if (this.username.equals(msg.getSuperuser())) {
                    promotion();
                }
                // Superuser update
            case "SUPERUSER":
                if (! this.username.equals(msg.getSuperuser()) && !this.prepareSignOut) {
                    updateSuperuser(msg.getSuperuserIp(), msg.getSuperuser());
                }
            default:
                break;
        }
    }

    private void processMembershipMsg(SpreadMessage message) throws SpreadException, IOException {
        boolean isSuperGroup = message.getMembershipInfo().getGroup().toString().contains("SuperGroup");

        if (! this.prepareSignOut && ! isSuperGroup) {
            processGroupMsg(message);
        }
        // Super user is preparing to sign out
        if (isSuperGroup){
            processSuperGroupMsg(message);
        }
    }

    private void processSuperGroupMsg(SpreadMessage message) throws SpreadException, IOException {
        SpreadGroup[] members = message.getMembershipInfo().getMembers();

        if(members != null) {
            this.connectedUsers = message.getMembershipInfo().getMembers().length;

            if (this.isPrepareSignOut() && this.connectedUsers == 1) {
                this.superGroup.leave();
                this.signedIn = false;

                String filename = this.username + ".db";

                // Serialization
                userSerialization(filename);

                System.exit(0);
            }
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
