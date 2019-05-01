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
        switch (msg.getType()) {
            case "POST":
                follower.updatePosts(getUsername(message.getSender().toString()), msg.getPosts(), true, "POST");
                break;
            case "POSTS":
                if (message.getMembershipInfo().getGroup().toString().split("Group")[0].equals(getUsername(message.getSender().toString()))) {
                    follower.updatePosts(getUsername(message.getSender().toString()), msg.getPosts(), true, "POSTS");
                }
                else {
                    // TODO: É suposto pedir posts a outro follower quando o follower lhe envia uma lista vazia de posts e status = OUTDATED?
                    follower.updatePosts(message.getMembershipInfo().getGroup().toString().split("Group")[0], msg.getPosts(), msg.getStatus(), "POSTS");
                }
                break;
            case "UPDATE":
                Msg reply = new Msg();
                reply.setType("POSTS");
                Pair<Boolean, List<Post>> pair = follower.getPosts(getUsername(message.getMembershipInfo().getGroup().toString()), msg.getLastPostId());
                reply.setPosts(pair.getSnd());
                reply.setStatus(pair.getFst());
                follower.sendMsg(reply, message.getSender().toString());
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
                if (! follower.checkPostsStatus(followee)) {
                    follower.sendPostsRequest(message.getMembershipInfo().getJoined().toString());
                }
            }

            // My login
            else if (joinedUser.equals(follower.getUsername())){
                follower.sendPostsRequest(selectMember(message, followee));
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
