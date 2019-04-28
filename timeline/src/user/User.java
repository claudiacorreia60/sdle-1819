package user;

import io.atomix.utils.serializer.Serializer;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import utils.Msg;
import utils.Pair;

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
    private Map<Integer, Post> myPosts;
    private Map<String, List<Pair<Boolean, Map<Integer, Post>>>> followees;
    private boolean isSuperuser;
    private Serializer serializer;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private BufferedReader input;
    private SpreadConnection connection;
    private SpreadGroup centralGroup;
    private boolean signedIn;


    // TODO: mudar login para não usar sockets


    public User (String hostname, int port) throws IOException {
        this.serializer = Serializer.builder()
                .withTypes(
                        Msg.class,
                        AbstractMap.SimpleEntry.class)
                .build();
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
            handleOption("SIGN IN");
        }
        else {
            handleOption("SIGN UP");
        }
    }

    public void handleOption (String choice) throws IOException {
        Msg reply = null;

        String result = "NACK";
        System.out.println("\n################## "+choice+" ###################");
        while (result.equals("NACK")) {
            System.out.print("> Username: ");
            this.username = this.input.readLine();
            System.out.print("> Password: ");
            this.password = this.input.readLine();
            Msg msg = new Msg();
            msg.setUsername(this.username);
            msg.setPassword(this.password);
            this.out.write(this.serializer.encode(msg));
            this.out.flush();
            byte [] response = this.receive();
            reply = this.serializer.decode(response);
            result = reply.getType();
            if (result.equals("NACK")) {
                System.out.println("Error: Incorrect credentials. Please try again.");
            }
        }
        this.signedIn = true;
        openSpreadConnection(reply.getSuperuser());
    }

    public void openSpreadConnection(String superuserAddress) throws UnknownHostException {
        try {

            //TODO: isto está mal, o user vai fazer join ao grupo da central antes do login
            // e depois vai fazer leave quando já tiver um superuser ao qual se conectar
            // por isso já não vai precisar da variável centralGroup
            this.connection.connect(
                    InetAddress.getByName(superuserAddress),
                    4803,
                    this.username,
                    false,
                    false);

            // Join central group
            this.centralGroup = new SpreadGroup();
            this.centralGroup.join(this.connection, "centralGroup");
        } catch (SpreadException e) {
            e.printStackTrace();
        }
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

    public void logout(){
        // TODO: o superuser fica a aguardar o disconnect da central
        // TODO: o user avisa a central quando faz logout

    }

    //TODO: quando o user se transforma em superuser tem que avisar a central do seu ip para ela o guardar
}
