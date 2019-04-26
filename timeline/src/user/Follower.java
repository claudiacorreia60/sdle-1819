import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import user.Post;
import utils.Msg;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;


public class Follower {
    private Address myAddress;
    //private String username;
    //private List<Post> posts; // ou Map<Integer, user.Post> posts
    private Map<String, List<Post>> followees;
    private Map<String, Boolean> followees_posts_status;
    private Serializer serializer;
    private SpreadConnection connection;
    private Map<String, SpreadGroup> groups;


    public Follower(Address myAddress, Map<String, List<Post>> followees, Map<String, Boolean> followees_posts_status, SpreadConnection connection, Map<String, SpreadGroup> groups) {
        this.myAddress = myAddress;
        this.followees = followees;
        this.followees_posts_status = followees_posts_status;
        this.serializer = Serializer.builder()
                .withTypes(
                        Msg.class,
                        AbstractMap.SimpleEntry.class)
                .build();
        this.connection = connection;
    }

    public void handleLogin() throws SpreadException {
        SpreadGroup group;
        for (String followee : this.followees.keySet()) {
            // Mark posts as OUTDATED
            this.followees_posts_status.put(followee, false);
            // Join followees' groups
            group = new SpreadGroup();
            group.join(this.connection, followee);
            this.groups.put(followee, group);
        }
        // Request posts
        for (Map.Entry<String, SpreadGroup> entry : this.groups.entrySet()) {
            SpreadGroup[] members = entry.getValue().getMembers();
            // TODO: Complete
        }
    }

    public void handleSubscription (String followee) {
        SpreadGroup group = new SpreadGroup();
        group.join(this.connection, followee);
        this.groups.put(followee, group);
        // TODO: Complete
    }
}
