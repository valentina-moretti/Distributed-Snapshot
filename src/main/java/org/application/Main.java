package org.application;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.project.SnapshotCreator;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Scanner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

public class Main {

    public static void main(String[] args) {
        Controller controller = new Controller();
        Gson gson = new Gson();

        //identifier
        int identifier;
        if (args.length == 0) {
            Scanner s = new Scanner(System.in);
            System.out.println("Identifier: ");
            try {
                Integer i = s.nextInt();
                identifier = i;
            } catch (Exception e) {
                identifier = 0;
            }

        } else {
            identifier = Integer.parseInt(args[0]);
        }
        System.out.println(identifier);
        controller.setIdentifier(identifier);
        //end identifier




        File file = new File("Objects"+identifier+".json");
        //if the file do not exist: is the first time I'm creating it
        if (file.length() == 0) {
            //server port
            int serverPort;
            if (args.length == 0) {
                Scanner s = new Scanner(System.in);
                System.out.println("Port: ");
                try {
                    Integer p = s.nextInt();
                    serverPort = p;
                } catch (Exception e) {
                    serverPort = 35002+identifier;
                }

            } else {
                serverPort = Integer.parseInt(args[0]);
            }
            System.out.println(serverPort);
            controller.setServerPort(serverPort);
            //end server port
            Thread controllerThread = new Thread() {
                public void run() {
                    controller.run();
                }
            };
            controllerThread.start();

        } else {

            //I'm recovering
            System.out.println("Recovering.");
            try {
                controller.recover();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Recovering completed.");

        }

        Farm f = controller.getFarm();
        f.addAnimal(new Animal("gatto"));

        System.out.println(gson.toJson(controller));


    }
}