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
    private boolean signedIn;

    public UserReceiver(Follower follower, Followee followee, boolean signedIn) {
        this.follower = follower;
        this.followee = followee;
        this.signedIn = signedIn;
    }

    @Override
    public void run() {
        while (this.signedIn) {
            SpreadMessage message = this.connection.receive();
            processMsg(message);
            String sender = message.getSender().toString();
            Msg msg = this.serializer.decode(message.getData());
            switch (msg.getType()) {
                case "POST":
                    Post post = msg.getPosts().get(0);
                    List<Post> posts = this.followees.get(sender);
                    posts.add(post);
                    this.followees.put(sender, posts);
                    break;
                case "POSTS":
                    // TODO: Complete
                    String private_group = message.getGroups()[0].toString();
                    if (private_group.equals(sender)) {

                    }
                    else {

                    }
            }
        }
    }

    private void processMsg(SpreadMessage message) {
        if (message.isRegular()) {
            processRegularMsg(message);
        }
        else {
            processMembershipMsg(message);
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
