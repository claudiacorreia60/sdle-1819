package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


public class Follower implements Serializable {
    private transient String username;
    private Map<String, Pair<Boolean, Map<Integer, Post>>> followees;
    private transient Serializer serializer;
    private transient SpreadConnection connection;
    private transient Map<String, SpreadGroup> followeesGroups;
    private transient BufferedReader input;
    private int receivedPostsMsgs;
    private List<Post> receivedPosts;


    public Follower(String username, Serializer serializer, SpreadConnection connection, BufferedReader input) {
        this.username = username;
        this.followees = new HashMap<>();
        this.serializer = serializer;
        this.connection = connection;
        this.followeesGroups = new HashMap<>();
        this.input = input;
        this.receivedPostsMsgs = 0;
        this.receivedPosts = new ArrayList<>();
    }

    public Map<String, Pair<Boolean, Map<Integer, Post>>> getFollowees() {
        return followees;
    }

    public void setFollowees(Map<String, Pair<Boolean, Map<Integer, Post>>> followees) {
        this.followees = followees;
    }

    public void signIn() throws SpreadException, InterruptedIOException {
        SpreadGroup group;
        for (Map.Entry<String, Pair<Boolean, Map<Integer, Post>>> entry : this.followees.entrySet()) {
            // Delete posts older than 2 minutes and update posts' status
            filterFolloweesPosts(2, "minute");
            // Join followees' groups
            group = new SpreadGroup();
            group.join(this.connection, entry.getKey()+"Group");
            this.followeesGroups.put(entry.getKey(), group);
        }
    }

    public void sendPostsRequest(String privateGroup, String followee) throws SpreadException {
        Msg msg = new Msg();
        msg.setType("UPDATE");
        msg.setFollowee(followee);
        msg.setLastPostId(findLastPostId(followee));
        sendMsg(msg, privateGroup);
    }

    public int findLastPostId (String username) {
        Map<Integer, Post> posts = this.followees.get(username).getSnd();
        if (posts.size() == 0) {
            return 0;
        }
        // Select next post to receive
        return Collections.max(posts.keySet()) + 1;
    }

    public void follow() {
        boolean success = false;
        String username = null;
        SpreadGroup group = null;
        while (! success) {
            success = true;
            System.out.print("\nUsername: ");
            try {
                username = this.input.readLine();
                group = new SpreadGroup();
                group.join(this.connection, username + "Group");
            } catch (SpreadException e) {
                success = false;
                System.out.println("Error: Please try again.");
            } catch (IOException e) {
                success = false;
                System.out.println("Error: Please try again.");
            }
        }
        this.followeesGroups.put(username, group);
        this.followees.put(username, new Pair<>(false, new HashMap<>()));
    }

    public void unfollow() {
        boolean success = false;
        while (! success) {
            success = true;
            System.out.print("\nUsername: ");
            try {
                String username = this.input.readLine();
                SpreadGroup group = this.followeesGroups.get(username);
                if (group == null) {
                    success = false;
                }
                else {
                    group.leave();
                    this.followees.remove(username);
                    this.followeesGroups.remove(username);
                }
            } catch (SpreadException e) {
                success = false;
                System.out.println("Error: Please try again.");
            } catch (IOException e) {
                success = false;
                System.out.println("Error: Please try again.");
            }
        }
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
        message.setCausal();
        message.setReliable();

        connection.multicast(message);
    }

    public boolean checkPostsStatus(String followee) {
        Pair<Boolean, Map<Integer, Post>> p = this.followees.get(followee);
        if (p != null) {
            return p.getFst();
        } else {
            return false;
        }
    }

    public void updatePosts(String followee, List<Post> posts, boolean postsStatus, String type) {
        Pair<Boolean, Map<Integer, Post>> pair = this.followees.get(followee);
        boolean myPost = true;
        Map<Integer, Post> followeePosts = null;

        if (pair != null) {
            myPost = false;
            followeePosts = pair.getSnd();
        }
        for (Post post : posts) {
            // Save followee's post
            if (!myPost) {
                followeePosts.put(post.getId(), post);
            }
            // Waits to receive all posts from the followees/followers
            if (this.receivedPostsMsgs < this.followees.size()){
                this.receivedPosts.add(post);
            }
            //
            else {
                System.out.println("\n################## NEW POST ##################");
                System.out.println("FROM: "+followee);
                System.out.println("DATE: "+post.getDate().getTime().toString());
                System.out.println("POST: "+post.getContent());
            }
        }

        if(!myPost) {
            // Update status of the list of posts
            if (type.equals("POSTS")) {
                pair.setFst(postsStatus);
                this.receivedPostsMsgs++;
            }
            pair.setSnd(followeePosts);
            this.followees.put(followee, pair);
        }
        if (this.receivedPostsMsgs == this.followees.size()) {
            printPosts();
        }
    }

    public void printPosts() {
        this.receivedPostsMsgs++;
        List<Post> result = this.receivedPosts.stream().sorted(Comparator.comparing(Post::getDate)).
                collect(Collectors.toList());
        for (Post post : result) {
            System.out.println("\n################## NEW POST ##################");
            System.out.println("FROM: "+post.getUsername());
            System.out.println("DATE: "+post.getDate().getTime().toString());
            System.out.println("POST: "+post.getContent());
        }
    }

    public Pair<Boolean, List<Post>> getPosts(String followee, int lastPostId) {
        List<Post> posts = new ArrayList<>(this.followees.get(followee).getSnd().values());
        List<Post> requestedPosts = posts.stream().filter(post -> post.getId() >= lastPostId)
                .collect(Collectors.toList());
        Pair<Boolean, List<Post>> pair = new Pair<>(this.followees.get(followee).getFst(), requestedPosts);
        return pair;
    }

    private void filterFolloweesPosts(int time, String unit) {
        for (Map.Entry<String, Pair<Boolean, Map<Integer, Post>>> entry : this.followees.entrySet()) {
            // Delete old posts
            Map<Integer, Post> posts = entry.getValue().getSnd();
            posts = posts.entrySet().stream()
                    .filter(x -> validateDate(x.getValue().getDate(), time, unit))
                    .collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue()));
            Pair<Boolean, Map<Integer, Post>> pair = entry.getValue();
            // Mark posts as OUTDATED
            pair.setFst(false);
            pair.setSnd(posts);
            this.followees.put(entry.getKey(), pair);
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
}
