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
        controller.setServerPort(serverPort);

        Thread controllerThread= new Thread(controller);
        controllerThread.start();
        Gson gson = new Gson();
        Farm f = controller.getFarm();

        System.out.println(gson.toJson(controller));


    }


}
