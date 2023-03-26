package org.application;

import com.google.gson.Gson;
import org.project.ControllerInterface;
import org.project.SnapshotCreator;

import java.io.*;
import java.net.InetAddress;
import java.util.Scanner;

public class Controller implements ControllerInterface {
    private Farm farm;
    private int serverPort;
    private transient SnapshotCreator sc;


    public Controller(int identifier, int serverPort) {

        this.serverPort = serverPort;
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
                    System.out.println(sc.getConnections());
                } else if (s.equals("give animal")) {
                    System.out.println("Select connection from: ");
                    System.out.println(sc.getConnections());
                    System.out.println("/ip-port: ");
                    s = console.readLine();
                    OutputStream outputStream = sc.getOutputStream(s);
                    PrintWriter out = new PrintWriter(outputStream, true);
                    System.out.println("Which animal do you want to donate? ");
                    s = console.readLine();
                    out.println("Add animal "+s);

                    //todo come si danno ordini regalo animale

                } else if (s.equals("read")) {
                    System.out.println("Select connection from: ");
                    System.out.println(sc.getConnections());
                    System.out.println("/ip-port: ");
                    s = console.readLine();
                    InputStream in = sc.getInputStream(s);
                    Scanner scanner = new Scanner(in).useDelimiter("\\A");
                    String result = scanner.hasNext() ? scanner.next() : "";
                    if(result!= null && result.length()>11) {
                        if (result.startsWith("Add animal ")) {
                            String animal = result.split("Add animal")[1];
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
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void Serialize() {
        Gson gson = new Gson();
        String serializedObjects = gson.toJson(this);
        File file = new File("Controller"+serverPort+".txt");
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
    public ControllerInterface Deserialize() throws FileNotFoundException {
        Gson gson = new Gson();
        ControllerInterface controller = null;
        try{
        File objectsFile = new File("lastSnapshot"+serverPort+".txt");
        Reader reader = new FileReader(objectsFile);
        Controller inObj = gson.fromJson(reader, Controller.class);
        if(inObj != null)
            controller = inObj;
        else {
            System.out.println("Controller file was corrupted");
            throw new ClassNotFoundException("Controller file was corrupted");
        }
        }catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new FileNotFoundException("File was corrupted");
        }
        return controller;
    }

    @Override
    public void SetSnapshotCreator(SnapshotCreator snapshotCreator) {
        this.sc=snapshotCreator;
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


}
