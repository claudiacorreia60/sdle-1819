package user;

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

//TODO: Passar os métodos da UserReceive para a User

public class UserReceiver implements Runnable {
    private User user;
    private int connectedUsers;

    public UserReceiver(User user) {
        this.user = user;
    }

    @Override
    public void run() {
        while (this.user.isSignedIn()) {
            try {
                SpreadMessage message = this.user.getConnection().receive();
                processMsg(message);
            } catch (SpreadException e) {
                e.printStackTrace();
            } catch (InterruptedIOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processMsg(SpreadMessage message) throws SpreadException, InterruptedIOException {
        if (message.isRegular()) {
            processRegularMsg(message);
        }
        else {
            processMembershipMsg(message);
        }
    }

    private void processRegularMsg(SpreadMessage message) throws SpreadException, InterruptedIOException {
        Msg msg = this.user.getSerializer().decode(message.getData());
        String followee = message.getMembershipInfo().getGroup().toString().split("Group")[0];
        switch (msg.getType()) {
            // Followee post
            case "POST":
                this.user.getFollower().updatePosts(getUsername(message.getSender().toString()), msg.getPosts(), true, "POST");
                break;
            // Follower receives posts
            case "POSTS":
                // Received posts from the followee
                if (followee.equals(getUsername(message.getSender().toString()))) {
                    this.user.getFollower().updatePosts(getUsername(message.getSender().toString()), msg.getPosts(), true, "POSTS");
                }
                // Received posts from a follower
                else {
                    this.user.getFollower().updatePosts(followee, msg.getPosts(), msg.getStatus(), "POSTS");
                }
                break;
            // Follower requests posts
            case "UPDATE":
                Msg reply = new Msg();
                reply.setType("POSTS");
                // I'm the followee
                if (this.user.getFollowee().getUsername().equals(followee)) {
                    // Get my posts
                    reply.setPosts(this.user.getFollowee().getPosts(msg.getLastPostId()));
                    reply.setStatus(true);
                    this.user.getFollowee().sendMsg(reply, message.getSender().toString());
                }
                // I'm a follower
                else {
                    // Get followee's posts
                    Pair<Boolean, List<Post>> pair = this.user.getFollower().getPosts(followee, msg.getLastPostId());
                    reply.setPosts(pair.getSnd());
                    reply.setStatus(pair.getFst());
                    this.user.getFollower().sendMsg(reply, message.getSender().toString());
                }
                break;
            // User gets promoted to superuser
            case "PROMOTION":
                this.user.promotion();
            // Superuser update
            case "SUPERUSER":
                this.user.updateSuperuser(msg.getSuperuserIp(), msg.getSuperuser());
            default:
                break;
        }
    }

    private void processMembershipMsg(SpreadMessage message) throws SpreadException {
        if(! this.user.isPrepareSignOut()) {
            // Find which followee group originated the message
            String followee = message.getMembershipInfo().getGroup().toString().split("Group")[0];

            if (message.getMembershipInfo().isCausedByJoin()) {
                String joinedUser = getUsername(message.getMembershipInfo().getJoined().toString());

                // Followee signIn
                if (joinedUser.equals(followee)) {
                    // If this followees' posts are outdated, send request
                    if (!this.user.getFollower().checkPostsStatus(followee)) {
                        this.user.getFollower().sendPostsRequest(message.getMembershipInfo().getJoined().toString());
                    }
                }

                // My signIn
                else if (joinedUser.equals(this.user.getFollower().getUsername())) {
                    this.user.getFollower().sendPostsRequest(selectMember(message, followee));
                }
            }
        }
        // Super user is preparing to sign out
        else{
            this.connectedUsers = message.getMembershipInfo().getMembers().length;

            if(this.connectedUsers == 1){
                this.user.getSuperGroup().leave();
                this.user.setSignedIn(false);
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
