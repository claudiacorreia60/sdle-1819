package main;

import spread.SpreadException;
import user.User;

import java.io.IOException;

public class UserMain {
    public static void main(String args[]) throws IOException, SpreadException {
        User user = new user.User("localhost");
    }
}
