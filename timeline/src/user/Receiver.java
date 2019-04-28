package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadMessage;
import utils.Msg;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;

public class Receiver implements Runnable {
    private Map<String, List<Post>> followees;
    private Map<String, Boolean> followees_posts_status;
    private Serializer serializer;
    private SpreadConnection connection;
    private boolean signedIn;

    public Receiver(Map<String, List<Post>> followees, Map<String, Boolean> followees_posts_status, Serializer serializer, SpreadConnection connection, boolean signedIn) {
        this.followees = followees;
        this.followees_posts_status = followees_posts_status;
        this.serializer = serializer;
        this.connection = connection;
        this.signedIn = signedIn;
    }
    @Override
    public void run() {
        while (this.signedIn) {
            SpreadMessage message = null;
            try {
                message = this.connection.receive();
            } catch (SpreadException e) {
                e.printStackTrace();
            } catch (InterruptedIOException e) {
                e.printStackTrace();
            }
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
}
