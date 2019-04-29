package user;

import io.atomix.utils.serializer.Serializer;

import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;

import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;

import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Follower {
    private String username;
    private Map<String, Pair<Boolean, Map<Integer, Post>>> followees;
    private Serializer serializer;
    private SpreadConnection connection;
    private Map<String, SpreadGroup> followeesGroups;


    public Follower(String username, Map<String, Pair<Boolean, Map<Integer, Post>>> followees, Serializer serializer, SpreadConnection connection) {
        this.username = username;
        this.followees = followees;
        this.serializer = serializer;
        this.connection = connection;
        this.followeesGroups = new HashMap<>();
    }

    public void login() throws SpreadException, InterruptedIOException {
        SpreadGroup group;
        for (Map.Entry<String, Pair<Boolean, Map<Integer, Post>>> entry : this.followees.entrySet()) {
            // Mark posts as OUTDATED
            Pair<Boolean, Map<Integer, Post>> posts = entry.getValue();
            posts.setFst(false);
            this.followees.put(entry.getKey(), posts);
            // Join followees' groups
            group = new SpreadGroup();
            group.join(this.connection, entry.getKey());
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
        // Select highest post ID
        return Collections.max(posts.keySet());
    }

    public void subscription (String followee) throws SpreadException {
        SpreadGroup group = new SpreadGroup();
        group.join(this.connection, followee);
        this.followeesGroups.put(followee, group);
        // TODO: Complete
    }

    public void logout(){
        // TODO: fazer leave dos followeesGroups
    }

    private void sendMsg(Msg m, String group) throws SpreadException {
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

    public String getUsername() {
        return username;
    }
}
