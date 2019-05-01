package user;

import io.atomix.utils.serializer.Serializer;

import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;

import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;

import java.io.InterruptedIOException;
import java.util.*;
import java.util.stream.Collectors;


public class Follower {
    private String username;
    private Map<String, Pair<Boolean, Map<Integer, Post>>> followees;
    private Serializer serializer;
    private SpreadConnection connection;
    private Map<String, SpreadGroup> followeesGroups;


    public Follower(String username, Serializer serializer, SpreadConnection connection) {
        this.username = username;
        // TODO: Recuperar informação de um ficheiro
        this.followees = new HashMap<>();
        this.serializer = serializer;
        this.connection = connection;
        this.followeesGroups = new HashMap<>();
    }

    public void signIn() throws SpreadException, InterruptedIOException {
        SpreadGroup group;
        for (Map.Entry<String, Pair<Boolean, Map<Integer, Post>>> entry : this.followees.entrySet()) {
            // Mark posts as OUTDATED
            Pair<Boolean, Map<Integer, Post>> posts = entry.getValue();
            posts.setFst(false);
            this.followees.put(entry.getKey(), posts);
            // Join followees' groups
            group = new SpreadGroup();
            group.join(this.connection, entry.getKey()+"Group");
            this.followeesGroups.put(entry.getKey(), group);
        }
    }

    public void sendPostsRequest(String privateGroup) throws SpreadException {
        Msg msg = new Msg();
        msg.setType("UPDATE");
        msg.setLastPostId(findLastPostId(getUsername(privateGroup)));
        sendMsg(msg, privateGroup);
    }

    public String getUsername (String privateGroup) {
        // Select private name from private group
        String[] parts = privateGroup.substring(1).split("#");
        return parts[0];
    }

    public int findLastPostId (String username) {
        Map<Integer, Post> posts = this.followees.get(username).getSnd();
        if (posts.size() == 0) {
            return 0;
        }
        // Select highest post ID
        return Collections.max(posts.keySet());
    }

    public void subscribe (String followee) throws SpreadException {
        SpreadGroup group = new SpreadGroup();
        group.join(this.connection, followee+"Group");
        this.followeesGroups.put(followee, group);
    }

    public void signOut() throws SpreadException {
        // Leave followee's groups
        for(SpreadGroup g : this.followeesGroups.values()){
            g.leave();
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

    public boolean checkPostsStatus(String followee) {
        return this.followees.get(followee).getFst();
    }

    public void updatePosts(String followee, List<Post> posts, boolean postsStatus, String type) {
        Pair<Boolean, Map<Integer, Post>> pair = this.followees.get(followee);
        Map<Integer, Post> followeePosts = pair.getSnd();
        for (Post post : posts) {
            followeePosts.put(post.getId(), post);
            System.out.println("################## NEW POST ##################");
            System.out.println("FROM: "+followee);
            System.out.println("DATE: "+post.getDate());
            System.out.println("POST: "+post.getContent());
        }
        if (type.equals("POSTS")) {
            pair.setFst(postsStatus);
        }
        pair.setSnd(followeePosts);
        this.followees.put(followee, pair);
    }

    public Pair<Boolean, List<Post>> getPosts(String followee, int lastPostId) {
        List<Post> posts = new ArrayList<>(this.followees.get(followee).getSnd().values());
        List<Post> requestedPosts = posts.stream().filter(post -> post.getId() > lastPostId)
                .collect(Collectors.toList());
        Pair<Boolean, List<Post>> pair = new Pair<>(this.followees.get(followee).getFst(), requestedPosts);
        return pair;
    }

    public String getUsername() {
        return username;
    }
}
