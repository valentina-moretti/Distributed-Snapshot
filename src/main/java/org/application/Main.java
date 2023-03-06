package org.application;


import org.project.SnapshotCreator;

import java.io.IOException;
import java.io.Serializable;

public class Main {

    public static void main(String[] args){
        Controller controller = controller.getInstance();
        int serverPort;
        if (args.length == 0) {
            serverPort = 35002;
        } else {
            serverPort = Integer.parseInt(args[0]);
        }
        controller.start(serverPort);

        try {
            SnapshotCreator snapshotCreator = new SnapshotCreator((Serializable) controller);
        } catch (IOException e) {
            e.printStackTrace();
        }
        controller.start_application();
        Recovery recovery= new Recovery();
        recovery.recover(controller);
    }


}
