package user;

import spread.SpreadException;
import utils.Msg;

import java.util.TimerTask;

public class SUTransform extends TimerTask {

    private User u;

    public SUTransform(User u) {
        this.u = u;
    }

    @Override
    public void run() {
        //TODO: Atualizar ficheiro de configuração do spread
        if (!u.isSuperuser()) {
            u.setSuperuser(true);

            Msg msg = new Msg();
            msg.setType("SUPERUSER");
            msg.setSuperuserIp(u.getMyAddress());
            try {
                u.sendMsg(msg, "centralGroup");
            } catch (SpreadException e) {
                e.printStackTrace();
            }
        }
    }
}
