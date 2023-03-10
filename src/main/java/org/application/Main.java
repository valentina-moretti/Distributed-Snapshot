package org.application;


import com.google.gson.Gson;
import org.project.SnapshotCreator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Scanner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

public class Main {

    public static void main(String[] args){
        Controller controller = Controller.getInstance();
        int serverPort;
        if (args.length == 0) {
            Scanner s = new Scanner(System.in);
            System.out.println("Port: ");
            try{
                Integer p = s.nextInt();
                serverPort = p;}
            catch(Exception e) {serverPort = 35002;}

        } else {
            serverPort = Integer.parseInt(args[0]);
        }
        System.out.println(serverPort);
        controller.setServerPort(serverPort);

        Thread controllerThread = new Thread() {
            public void run() {
                controller.run();
            }
        };
        controllerThread.start();
        Gson gson = new Gson();
        Farm f = controller.getFarm();
        f.addAnimal(new Animal("gatto"));

        System.out.println(gson.toJson(controller));



    }


}
