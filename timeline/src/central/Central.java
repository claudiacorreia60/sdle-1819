package central;

import io.atomix.utils.net.Address;
import io.atomix.utils.serializer.Serializer;

public class Central {
    private SpreadConnection connection;
    private Serializer s;
    private Address myAddress;
}
