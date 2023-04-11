package org.application;

import com.google.gson.Gson;
import org.project.ControllerInterface;
import org.project.SnapshotCreator;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Controller implements ControllerInterface {
    private Farm farm;
    private int serverPort;
    private transient SnapshotCreator sc;
    private final int identifier;
    private transient boolean stop;


    public Controller(int identifier, int serverPort) {
        this.stop = false;
        this.identifier = identifier;
        this.serverPort = serverPort;
        this.farm = new Farm(this);
        try {
            sc = new SnapshotCreator(this, identifier, serverPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        stop = false;
        String s = "";
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while (!s.equals("quit") && !stop) {
            if (sc.ControllerHasToStop()) stop();
            System.out.println("> ");
            try {
                s = console.readLine();
                if (sc.ControllerHasToStop()){
                    stop();
                    System.out.println("Stopping controller");
                    return;
                }
                else if (s.equals("connect")) {
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
                    System.out.println(sc.getConnections());
                } else if (s.equals("give animal")) {
                    if(sc.getConnections().size()==1){
                        s = sc.getConnections().iterator().next();
                    }
                    else {
                        System.out.println("Select connection from: ");
                        System.out.println(sc.getConnections());
                        System.out.println("/ip-port: ");
                        s = console.readLine();
                    }
                    OutputStream outputStream = sc.getOutputStream(s);
                    PrintWriter out = new PrintWriter(outputStream, true);
                    System.out.println("Which animal do you want to donate? ");
                    s = console.readLine();
                    out.println("Add animal "+s);

                } else if (s.equals("read")) {
                    if(sc.getConnections().size()==1){
                        s = sc.getConnections().iterator().next();
                    }
                    else {
                        System.out.println("Select connection from: ");
                        System.out.println(sc.getConnections());
                        System.out.println("/ip-port: ");
                        s = console.readLine();
                    }
                    InputStream in = sc.getInputStream(s);
                    BufferedInputStream bis = new BufferedInputStream(in);
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    for (int result = bis.read(); result != -1; result = bis.read()) {
                        buf.write((byte) result);
                    }
                    String result = buf.toString(StandardCharsets.UTF_8);
                    System.out.println(result);

                    if (result.length() > 11) {
                        if (result.startsWith("Add animal ")) {
                            String animal = result.split("Add animal ")[1];
                            this.farm.addAnimal(new Animal(animal));
                        }
                    }

                } else if (s.equals("ip")) {
                    System.out.println("Your IP: " + InetAddress.getLocalHost());
                } else if (s.equals("snap")) {
                    sc.startSnapshot();
                } else if (s.equals("farm")) {
                    for (Animal a :
                            this.farm.getAnimalList()) {
                        System.out.println(a.getName());
                    }
                } else if (s.equals("auto")) {

                    Scanner scanner = new Scanner(System.in);
                    System.out.println("Port: ");
                    Integer p = null;
                    try {
                        p = scanner.nextInt();
                        sc.connect_to(InetAddress.getLocalHost(), p);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("Getting OutputStream");
                    OutputStream outputStream = sc.getOutputStream(sc.getConnections().iterator().next());
                    PrintWriter out = new PrintWriter(outputStream, true);
                    System.out.println("Sending pollo");
                    out.println("Add animal pollo");
                    System.out.println("Pollo sent.");

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void stop()
    {
        this.stop = true;
    }

    @Override
    public void Serialize() {
        Gson gson = new Gson();
        String serializedObjects = gson.toJson(this);
        File file = new File("Controller"+identifier+".txt");
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos);
             BufferedWriter writer = new BufferedWriter(osw))
        {
            writer.write(serializedObjects);
        } catch (IOException fileNotFoundException)
        {
            fileNotFoundException.printStackTrace();
        }
    }

    @Override
    public void SetSnapshotCreator(SnapshotCreator snapshotCreator) {
        this.sc=snapshotCreator;
    }


    public Farm getFarm(){
        return this.farm;
    }


}
