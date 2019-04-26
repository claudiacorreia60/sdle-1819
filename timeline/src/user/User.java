import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import user.Post;
import utils.Msg;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

public class User {
    private String myAddress;
    private String username;
    private String password;
    private List<Post> posts; // ou Map<Integer, user.Post> posts
    private Map<String, List<Post>> followees;
    private Map<String, Boolean> followees_posts_status;
    private String superuser;
    private boolean is_superuser;
    private String central_group;
    private Serializer serializer;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private BufferedReader input;
    private SpreadConnection connection;
    private Map<String, SpreadGroup> groups;
    private boolean signedIn;


    public User (String hostname, int port) throws IOException {
        this.serializer = Serializer.builder()
                .withTypes(
                        Msg.class,
                        AbstractMap.SimpleEntry.class)
                .build();
        this.central_group = "centralGroup";
        this.socket = new Socket(hostname, port);
        this.in = this.socket.getInputStream();
        this.out = this.socket.getOutputStream();
        this.input = new BufferedReader(new InputStreamReader(System.in));
        this.connection = new SpreadConnection();
        this.signedIn = false;
    }

    public void menu() throws IOException, SpreadException {
        System.out.println("#################### MENU ####################");
        System.out.println("      (1) Sign in      |      (2) Sign up     ");
        String read = this.input.readLine();
        while (!read.equals("1") && !read.equals("2")) {
            System.out.println("Error: Incorrect option. Please try again.");
            read = this.input.readLine();
        }
        if (read.equals("1")) {
            handleChoice("SIGN IN");
        }
        else {
            handleChoice("SIGN UP");
        }
    }

    public void handleChoice (String choice) throws IOException, SpreadException {
        String result = "NACK";
        System.out.println("\n################## "+choice+" ###################");
        while (result.equals("NACK")) {
            System.out.print("> Username: ");
            this.username = this.input.readLine();
            System.out.print("> Password: ");
            this.password = this.input.readLine();
            Msg msg = new Msg(this.username, this.password);
            this.out.write(this.serializer.encode(msg));
            this.out.flush();
            byte [] response = this.receive();
            Msg reply = this.serializer.decode(response);
            result = reply.getType();
            if (result.equals("NACK")) {
                System.out.println("Error: Incorrect credentials. Please try again.");
            }
            else {
                this.superuser = reply.getSuperuser();
            }
        }
        this.signedIn = true;
        openSpreadConnection();
    }

    public void openSpreadConnection() throws SpreadException, UnknownHostException {
        this.connection.connect(
                InetAddress.getByName("localhost"),
                4803,
                this.username,
                false,
                false);

        SpreadGroup group = new SpreadGroup();
        group.join(this.connection, this.username);
        this.groups.put(this.username, group);
        group = new SpreadGroup();
        group.join(this.connection, this.central_group);
        this.groups.put(this.central_group, group);
    }

    public byte[] receive(){
        byte[] tmp = new byte[4096];
        int len = 0;
        try {
            len = this.in.read(tmp);
            byte[] response = new byte[len];

            for(int i = 0; i < len; i++)
                response[i] = tmp[i];
            return response;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
