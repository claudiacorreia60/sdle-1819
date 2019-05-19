package main;

import spread.SpreadException;
import user.User;

import java.io.IOException;

public class UserMain {
    public static void main(String args[]) throws IOException, SpreadException {
        Process spread = Runtime.getRuntime().exec("spread -c spread.conf");
        User user = new user.User("localhost");
    }
}
