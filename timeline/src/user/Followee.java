package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Followee {
    private String username;
    private Map<Integer, Post> myPosts;
    private Serializer serializer;
    private SpreadConnection connection;
    private SpreadGroup myGroup;

    public Followee(String username, Serializer serializer, SpreadConnection connection) {
        this.username = username;
        // TODO: Recuperar informação de um ficheiro
        this.myPosts = new HashMap<>();
        this.serializer = serializer;
        this.connection = connection;
        this.myGroup = new SpreadGroup();
    }

    public void login(){
        try {
            this.myGroup.join(this.connection, this.username);
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

    public void post(Post post){
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

    public void logout(){
        // TODO: fazer leave do meu grupo
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
