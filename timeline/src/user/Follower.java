package user;

import io.atomix.utils.serializer.Serializer;

import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;

import utils.Pair;

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

    public void login() throws SpreadException {
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
        // Request posts
        for (Map.Entry<String, SpreadGroup> entry : this.followeesGroups.entrySet()) {
            SpreadGroup[] members = entry.getValue().getMembers();
            // TODO: Complete
        }
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
}
