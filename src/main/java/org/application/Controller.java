package org.application;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.project.SnapshotCreator;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Controller implements Serializable {
    private List<Object> objectList;
    private Farm farm;
    private int serverPort;
    private transient SnapshotCreator sc;
    private int identifier;


    public Controller(int identifier, int serverPort) {
        this.identifier = identifier;
        this.serverPort = serverPort;
        this.objectList = new ArrayList<>();
        this.farm = new Farm(this);
        try {
            sc = new SnapshotCreator(this, identifier, serverPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
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
                            String animal = s.split("Add animal")[1];
                            this.farm.addAnimal(new Animal(animal));
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


    public Farm getFarm(){
        return this.farm;
    }
    /*

    public Farm recoverFarm(List<Object> list) throws ClassNotFoundException {
        System.out.println(list);
        System.out.println("Objects:");
        for (Object o :
                list) {
            System.out.println(o);
            if (o instanceof Controller) {
                System.out.println("Farm detected");
                return ((Controller) o).getFarm();

            }
        }
        throw new ClassNotFoundException();
    }

     */

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
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
    /*

    void recover() throws IOException {
        Gson gson = new Gson();

        //Port Recovery
        BufferedReader in = new BufferedReader(new FileReader("Port"+identifier+".json"));
        this.serverPort = gson.fromJson(in, Integer.class);

        //Objects Recovery

        File file = new File("Objects"+identifier+".json");

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {
            String fileContent = reader.readLine();

            // Deserializzazione
            Type type = new TypeToken<List<Object>>() {}.getType();
            List<Object> deserializedObjects = gson.fromJson(fileContent, type);
            this.farm = recoverFarm(deserializedObjects);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }


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



    public void setIdentifier(int identifier) {
        this.identifier = identifier;
    }

     */

    public void setObjectList(ArrayList<Object> objectList) {
        this.objectList = objectList;
    }
}
