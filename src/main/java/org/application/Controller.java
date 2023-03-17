package org.application;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.project.SnapshotCreator;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

//TODO: extend thread
public class Controller implements Serializable {
    private List<Object> objectList;
    private Farm farm;
    private int serverPort;
    private transient SnapshotCreator sc;
    // private static Controller instance;
    private int identifier;
/*
    public static Controller getInstance() {
        if (instance == null) {
            instance = new Controller();
        }
        return instance;
    }

 */

    public Controller(int identifier, int serverPort) {
        this.identifier = identifier;
        this.serverPort = serverPort;
        this.objectList = new ArrayList<>();
        this.farm = new Farm(this);
    }

    public void run() {
        try {
            sc = new SnapshotCreator(this, identifier, serverPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String s = "";
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        //todo: s non puo essere ancora quit
        while (!s.equals("quit")) {
            System.out.println("> ");
            try {
                s = console.readLine();
                if (s.equals("connect")) {
                    System.out.println("ip: ");
                    s = console.readLine();
                    Scanner scanner = new Scanner(System.in);
                    System.out.println("Port: ");
                    try {
                        Integer p = scanner.nextInt();
                        sc.connect_to(InetAddress.getByName(s), p);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (s.equals("connections")) {
                    System.out.println(sc.getNameToConnection());
                } else if (s.equals("give animal")) {
                    System.out.println("Select connection from: ");
                    System.out.println(sc.getNameToConnection());
                    System.out.println("/ip-port: ");
                    s = console.readLine();
                    OutputStream outputStream = sc.getOutputStream(s);
                    PrintWriter out = new PrintWriter(outputStream, true);
                    System.out.println("Which animal do you want to donate? ");
                    s = console.readLine();
                    out.println("Add animal "+s);

                    //todo come si danno ordini regalo animale

                } else if (s.equals("read")) {
                    s= sc.readMessages();
                    if(s.length()>11) {
                        if (s.substring(0, 11).equals("Add animal ")) {
                            getFarm().addAnimal(new Animal(s.substring(11, s.length()-11)));
                        }
                    }
                } else if (s.equals("ip")) {
                    System.out.println("Your IP: " + InetAddress.getLocalHost());
                } else if (s.equals("serialize")) {
                    System.out.println("Serialization");
                    Serialize();
                } else if (s.equals("snap")) {
                    sc.startSnapshot();
                } else if (s.equals("farm")) {
                    System.out.println(this.farm);
                } else if (s.equals("add animal")) {
                    System.out.println("Animal: ");
                    s = console.readLine();
                    this.farm.addAnimal(new Animal(s));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Farm getFarm(){
        return this.farm;
    }
/*
    public Farm recoverFarm(List<Object> list) {
        System.out.println(list);
        return ((Controller) list.get(0)).getFarm();
    }

 */

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    void read() {
        sc.readMessages();
    }

    // For testing

    void Serialize() {
        sc.saveState();
    }

    public SnapshotCreator getSc() {
        return sc;
    }
/*
    void recover() throws IOException {
        Gson gson = new Gson();

        //Port Recovery
        BufferedReader in = new BufferedReader(new FileReader("Port"+identifier+".json"));
        this.serverPort = gson.fromJson(in, Integer.class);

        //Objects Recovery

        JsonReader reader = new JsonReader(new FileReader("Objects"+identifier+".json"));
        List<Object> list = gson.fromJson(reader, new TypeToken<List<Object>>(){}.getType());
        recoverFarm(list);



        Thread controllerThread = new Thread() {
            public void run() {
                try {
                    wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.run();
            }
        };
        controllerThread.start();
        //todo: there is no need of message recovery, right?
    }

 */

    public void setIdentifier(int identifier) {
        this.identifier = identifier;
    }

}
