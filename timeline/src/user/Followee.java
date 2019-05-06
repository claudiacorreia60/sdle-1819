package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


public class Followee {
    private String username;
    private Serializer serializer;
    private SpreadConnection connection;
    private Map<Integer, Post> myPosts;
    private SpreadGroup myGroup;
    private BufferedReader input;
    private User user;

    public Followee(String username, Serializer serializer, SpreadConnection connection, BufferedReader input, User user) throws IOException, SpreadException {
        this.username = username;
        // TODO: Recuperar informação de um ficheiro
        this.myPosts = new HashMap<>();
        this.serializer = serializer;
        this.connection = connection;
        this.myGroup = new SpreadGroup();
        this.input = input;
        this.user = user;
    }

    public void signIn() throws IOException {
        try {
            // Join my own followee group
            this.myGroup.join(this.connection, this.username + "Group");
            // Delete posts older than 5 minutes
            filterPosts(5, "minute");
        } catch (SpreadException e) {
            e.printStackTrace();
        }

        //TODO: Caso não tenha informação local, envia uma mensagem para o grupo a pedir os dados dele
    }

    public void subscribe(String follower){
        Msg msg = new Msg();
        msg.setType("SUBSCRIPTION");
        msg.setPosts(new ArrayList<>(this.myPosts.values()));

        try {
            sendMsg(msg, follower);
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    public void updatePosts(String follower){
        Msg msg = new Msg();
        msg.setType("POSTS");
        msg.setPosts(new ArrayList<>(this.myPosts.values()));

        try {
            sendMsg(msg, follower);
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    public List<Post> getPosts(int lastPostId) {
        List<Post> requestedPosts = this.myPosts.values().stream().filter(post -> post.getId() > lastPostId)
                .collect(Collectors.toList());
        return requestedPosts;
    }

    public void post() throws IOException {
        System.out.println("\nWrite your post:");
        String content = this.input.readLine();
        // Get current date
        Calendar date = Calendar.getInstance();
        // Get post ID
        int id = postId();
        // Store post
        Post post = new Post(id, date, content);
        this.myPosts.put(id, post);
        // Send post
        sendPost(post);
    }

    private int postId() {
        if (this.myPosts.isEmpty()) {
            return 0;
        }
        else {
            return Collections.max(this.myPosts.keySet()) + 1;
        }
    }

    public void sendPost(Post post){
        Msg msg = new Msg();
        msg.setType("POST");
        List<Post> posts = new ArrayList<>();
        posts.add(post);
        msg.setPosts(posts);

        // Send new post too all my followers
        try {
            sendMsg(msg, this.username + "Group");
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }

    private void filterPosts(int time, String unit) {
        Post post = this.myPosts.get(postId());

        // There are no posts
        if (post != null) {
            // Delete old posts
            this.myPosts = this.myPosts.entrySet().stream()
                    .filter(x -> validateDate(x.getValue().getDate(), time, unit))
                    .collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue()));
            if (this.myPosts.isEmpty()) {
                this.myPosts.put(post.getId(), post);
            }
        }
    }

    private boolean validateDate(Calendar postDate, int time, String unit) {
        Calendar now = Calendar.getInstance();
        long nowMilis = now.getTimeInMillis();
        long postDateMilis = postDate.getTimeInMillis();
        long diff = nowMilis - postDateMilis;
        switch (unit) {
            case "year":
                if (diff / (365 * 24 * 60 * 60 * 1000) < time) {
                    return true;
                }
                else {
                    return false;
                }
            case "month":
                if (diff / (30 * 24 * 60 * 60 * 1000) < time) {
                    return true;
                }
                else {
                    return false;
                }
            case "day":
                if (diff / (24 * 60 * 60 * 1000) < time) {
                    return true;
                }
                else {
                    return false;
                }
            case "hour":
                if (diff / (60 * 60 * 1000) < time) {
                    return true;
                }
                else {
                    return false;
                }
            case "minute":
                if (diff / (60 * 1000) < time) {
                    return true;
                }
                else {
                    return false;
                }
            default:
                break;
        }

        return false;
    }

    public void signOut() throws SpreadException {
        // Leave my group
        this.myGroup.leave();
    }

    public void sendMsg(Msg m, String group) throws SpreadException {
        SpreadMessage message = new SpreadMessage();

        message.setData(this.serializer.encode(m));
        message.addGroup(group);
        message.setAgreed();
        message.setReliable();

        connection.multicast(message);
    }

    public String getUsername() {
        return this.username;
    }
}
