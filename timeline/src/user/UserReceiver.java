package user;

import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;
import utils.Msg;
import utils.Pair;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class UserReceiver implements Runnable {
    private User user;

    public UserReceiver(User user) {
        this.user = user;
    }

    @Override
    public void run() {
        while (this.user.isSignedIn()) {
            try {
                SpreadMessage message = this.user.getConnection().receive();
                this.user.processMsg(message);
            } catch (SpreadException e) {
                e.printStackTrace();
            } catch (InterruptedIOException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
