package org.application;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.project.SnapshotCreator;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Controller implements Serializable {
    private ArrayList<Object> objectList;
    private int serverPort;
    private transient SnapshotCreator sc;
    private static Controller instance;
    private int identifier;

    public static Controller getInstance() {
        if (instance == null) {
            instance = new Controller();
        }
        return instance;
    }

    private Controller() {
        instance = null;
        this.objectList = new ArrayList<Object>();
        Farm farm = new Farm(this);
        objectList.add(farm);
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
                }
                // per debug
                else if (s.equals("serialize")) {
                    System.out.println("Serialization");
                    Serialize();
                } else if (s.equals("snap")) {
                    sc.startSnapshot();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Farm getFarm() {
        return (Farm) objectList.get(0);
    }

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
        sc.SerializeMessages();
        sc.SerializeConnections();
        sc.SerializeObjects();
    }

    public SnapshotCreator getSc() {
        return sc;
    }

    void recover() throws IOException {
        Gson gson = new Gson();

        //Port Recovery
        BufferedReader in = new BufferedReader(new FileReader("Port"+identifier+".json"));
        this.serverPort = gson.fromJson(in, Integer.class);

        //Objects Recovery
        in = new BufferedReader(new FileReader("Objects"+identifier+".json"));
        this.objectList = gson.fromJson(in, new TypeToken<ArrayList<Object>>() {
        }.getType());



        Thread controllerThread = new Thread() {
            public void run() {
                this.run();
            }
        };
        controllerThread.start();
        //todo: there is no need of message recovery, right?
    }

    public void setIdentifier(int identifier) {
        this.identifier = identifier;
    }

    public void setObjectList(ArrayList<Object> objectList) {
        this.objectList = objectList;
    }
}