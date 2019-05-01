package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;

import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class UserReceiver implements Runnable {
    private Follower follower;
    private Followee followee;
    private SpreadConnection connection;
    private Serializer serializer;
    private boolean signedIn;

    public UserReceiver(Follower follower, Followee followee, SpreadConnection connection, Serializer serializer, boolean signedIn) {
        this.follower = follower;
        this.followee = followee;
        this.connection = connection;
        this.serializer = serializer;
        this.signedIn = signedIn;
    }

    @Override
    public void run() {
        while (this.signedIn) {
            try {
                SpreadMessage message = this.connection.receive();
                processMsg(message);
            } catch (SpreadException e) {
                e.printStackTrace();
            } catch (InterruptedIOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processMsg(SpreadMessage message) throws SpreadException {
        if (message.isRegular()) {
            processRegularMsg(message);
        }
        else {
            processMembershipMsg(message);
        }
    }

    private void processRegularMsg(SpreadMessage message) throws SpreadException {
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
            default:
                break;
        }
    }

    private void processMembershipMsg(SpreadMessage message) throws SpreadException {
        // Find which followee group originated the message
        String followee = message.getMembershipInfo().getGroup().toString().split("Group")[0];

        if (message.getMembershipInfo().isCausedByJoin()) {
            String joinedUser = getUsername(message.getMembershipInfo().getJoined().toString());

            // Followee login
            if (joinedUser.equals(followee)) {
                // If this followees' posts are outdated, send request
                if (! this.follower.checkPostsStatus(followee)) {
                    this.follower.sendPostsRequest(message.getMembershipInfo().getJoined().toString());
                }
            }

            // My login
            else if (joinedUser.equals(this.follower.getUsername())){
                this.follower.sendPostsRequest(selectMember(message, followee));
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
