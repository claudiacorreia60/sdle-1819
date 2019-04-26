package central;

import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;
import utils.Msg;
import utils.Pair;

import java.net.InetAddress;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class Central {
    private SpreadConnection connection;
    private Serializer s;
    private String myAddress;
    private Map<String, Boolean> superusers;
    private Map<String, String> users;

    public Central(String myAddress) {
        this.connection = new SpreadConnection();
        this.s = this.s = Serializer.builder()
                .withTypes(
                        Msg.class,
                        Pair.class)
                .build();
        this.myAddress = myAddress;
        this.superusers = new HashMap<>();
        this.users = new HashMap<>();

        connection.connect(InetAddress.getByName(myAddress), 0, "central-"+myAddress, false, false);

        SpreadGroup group = new SpreadGroup();
        group.join(connection, "centralGroup");
    }

    public start() {
        while(true) {
            SpreadMessage message = connection.receive();

            if (message.isRegular()) {
                Msg msg = this.s.decode(message.getData());

                switch (msg.getType()) {

                    default:
                        throw new IllegalStateException("Unexpected value: " + msg.getType());
                }
            }
        }
    }
}
