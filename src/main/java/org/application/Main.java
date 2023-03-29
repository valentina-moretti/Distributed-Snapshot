package org.application;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.project.ControllerInterface;
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
        int identifier;
        //identifier
        if (args.length == 0) {
            Scanner s = new Scanner(System.in);
            System.out.println("Identifier: ");
            try {
                identifier = s.nextInt();
            } catch (Exception ee) {
                identifier = 0;
            }

        } else {
            identifier = Integer.parseInt(args[0]);
        }
        System.out.println(identifier);

        try {
            SnapshotCreator.snapshotDeserialization(identifier);
            System.out.println("Deserialized.");
        }catch (FileNotFoundException e){
            System.out.println(e);
            //server port
            int serverPort;
            if (args.length == 0) {
                Scanner s = new Scanner(System.in);
                System.out.println("Port: ");
                try {
                    serverPort = s.nextInt();
                } catch (Exception ee) {
                    serverPort = 35002 + identifier;
                }

            } else {
                serverPort = Integer.parseInt(args[0]);
            }
            System.out.println(serverPort);
            //end server port

            Controller controller = new Controller(identifier, serverPort);
            Thread controllerThread = new Thread(controller);
            controllerThread.start();

            Farm f = controller.getFarm();
            f.addAnimal(new Animal("gatto"));
        }
    }
}