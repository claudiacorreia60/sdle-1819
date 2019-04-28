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
    private Map<String, List<Pair<Boolean, Map<Integer, Post>>>> followees;
    private Serializer serializer;
    private SpreadConnection connection;
    private Map<String, SpreadGroup> followeesGroups;


    public Follower(String username, Map<String, List<Pair<Boolean, Map<Integer, Post>>>> followees, Serializer serializer, SpreadConnection connection) {
        this.username = username;
        this.followees = followees;
        this.serializer = serializer;
        this.connection = connection;
        this.followeesGroups = new HashMap<>();
    }

    public void login() throws SpreadException {
        //TODO: atualizar isto de acordo com a nova estrutura de followees
        SpreadGroup group;
        for (String followee : this.followees.keySet()) {
            // Mark posts as OUTDATED
            this.followees_posts_status.put(followee, false);
            // Join followees' groups
            group = new SpreadGroup();
            group.join(this.connection, followee);
            this.followeesGroups.put(followee, group);
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
