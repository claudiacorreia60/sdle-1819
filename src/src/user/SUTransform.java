package user;

import spread.SpreadException;
import utils.Msg;

import java.util.TimerTask;

public class SUTransform extends TimerTask {

    private User user;

    public SUTransform(User u) {
        this.user = u;
    }

    @Override
    public void run() {
        //TODO: Atualizar ficheiro de configuração do spread
        if (!user.isSuperuser()) {
            try {
                user.becomeSuperuser();
            } catch (SpreadException e) {
                e.printStackTrace();
            }
        }
    }
}
