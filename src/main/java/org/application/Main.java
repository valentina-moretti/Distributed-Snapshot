package org.application;


import com.google.gson.Gson;
import org.project.SnapshotCreator;

import java.io.IOException;
import java.io.Serializable;

public class Main {

    public static void main(String[] args){
        Controller controller = Controller.getInstance();
        int serverPort;
        if (args.length == 0) {
            serverPort = 35002;
        } else {
            serverPort = Integer.parseInt(args[0]);
        }
        SnapshotCreator snapshotCreator = null;
        try {
            snapshotCreator = new SnapshotCreator((Serializable) controller);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread controllerThread= new Thread(controller);
        controllerThread.run();
        Gson gson = new Gson();
        snapshotCreator.SaveState();
        SnapshotCreator sc = snapshotCreator.SnapshotDeserialization();
        System.out.println(gson.toJson(sc));




    }


}
