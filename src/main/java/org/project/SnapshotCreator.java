package org.project;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.application.Controller;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class SnapshotCreator
{
    static int serverPort;
    //todo:non usiamo piu i serializable
    private ArrayList<Object> contextObjects;
    private Controller controller;
    private MessageBuffer messages;
    private List<String> connectionNames;
    private transient Map<String, ConnectionManager> nameToConnection;
    private transient List<ConnectionManager> connections;
    private transient ConnectionAccepter connectionAccepter;
    private transient JsonConverter jsonConverter;
    private boolean snapshotting;
    private transient Map<String, Boolean> snapshotArrivedFrom;
    private Map<String, ArrayList<Byte>> savedMessages;
    static int identifier;

    static public SnapshotCreator snapshotDeserialization() throws FileNotFoundException
    {
        SnapshotCreator recoveredSystem = null;
        Map<String, ArrayList<Byte>> messages = null;
        //TODO: dovrei eseguire il metodo/i metodi che l'applicazione mi ha passato per riavviarla
        // i messaggi salvati li devo mettere nel buffer non in savedMessages
        // se non ci sono i file lancio FileNotFountException
        try{
            File messagesFile = new File("savedMessages");
            FileInputStream file = new FileInputStream(messagesFile);
            ObjectInputStream fileIn = new ObjectInputStream(file);

            Object inObj = fileIn.readObject();
            if(inObj instanceof Map)
                messages = (Map<String, ArrayList<Byte>>) inObj;
            else
                throw new ClassNotFoundException("Saved messages file was corrupted");

            fileIn.close();
            file.close();


            messagesFile = new File("lastSnapshot");
            file = new FileInputStream(messagesFile);
            fileIn = new ObjectInputStream(file);

            inObj = fileIn.readObject();
            if(inObj instanceof SnapshotCreator)
                recoveredSystem = (SnapshotCreator) inObj;
            else
                throw new ClassNotFoundException("State file was corrupted");

            fileIn.close();
            file.close();
        }catch (IOException | ClassNotFoundException e) {
            throw new FileNotFoundException("File was corrupted");
        }
        synchronized (recoveredSystem) { recoveredSystem.savedMessages = messages; };

        recoveredSystem.startController();
        System.out.println("Recovered Controller is running.");
        return recoveredSystem;
    }

    public void startController(){
        this.controller.run();
    };

    public SnapshotCreator(Controller controller, int identifier, int serverPort) throws IOException
    // TODO: there should be another parameter: the function to
    //  be executed when reloading from a previous snapshot
    {
        connectionNames = new ArrayList<>();
        contextObjects = new ArrayList<>();
        contextObjects.add(controller);
        messages = new MessageBuffer(this);
        nameToConnection = new HashMap<>();
        connections = new ArrayList<>();
        connectionAccepter = new ConnectionAccepter(this);
        connectionAccepter.start();
        snapshotting = false;
        snapshotArrivedFrom = new HashMap<>();
        savedMessages = new HashMap<String, ArrayList<Byte>>();
        SnapshotCreator.serverPort = serverPort;
        SnapshotCreator.identifier = identifier;
        this.controller = controller;

    }
/*
    void Recover() throws IOException {
        Gson gson = new Gson();


        //Port
        BufferedReader in = new BufferedReader(new FileReader("Port"+identifier+".json"));
        SnapshotCreator.serverPort = gson.fromJson(in, Integer.class);


        //Objects
        in = new BufferedReader(new FileReader("Objects"+identifier+".json"));
        this.contextObjects = gson.fromJson(in, new TypeToken<ArrayList<Object>>(){}.getType());

        //Connections
        in = new BufferedReader(new FileReader("Connections"+identifier+".json"));
        ArrayList<String> oldConnections = gson.fromJson(in, new TypeToken<ArrayList<String>>(){}.getType());

        for (String connection:
                oldConnections) {
            connection = connection.substring(1);
            String[] ipAndPort = connection.split("-");
            try{
                connect_to(InetAddress.getByName(ipAndPort[0]), Integer.valueOf(ipAndPort[1]));
            } catch (Exception e){
                e.printStackTrace();
            }

        }

        //Messages
        in = new BufferedReader(new FileReader("Messages"+identifier+".json"));
        this.savedMessages = gson.fromJson(in, new TypeToken<Map<String, ArrayList<Byte>>>(){}.getType());

    }

 */


    synchronized void connectionAccepted(Socket connection)
    {
        String name = connection.getInetAddress().toString() + "-" + connection.getPort();
        ConnectionManager newConnectionM = new ConnectionManager(connection, name, messages);
        connectionNames.add(name);
        connections.add(newConnectionM);
        messages.addClient(name);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
    }

    synchronized public String connect_to(InetAddress address, Integer port) throws IOException
    {
        String name = address.toString() + "-" + port;
        Socket socket = new Socket(address, port);
        connectionNames.add(name);
        ConnectionManager newConnectionM = new ConnectionManager(socket, name, messages);
        connections.add(newConnectionM);
        messages.addClient(name);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
        return name;
    }

    synchronized public InputStream getInputStream(String connectionName)
    {
        return new MyInputStream(messages, connectionName);
    }

    synchronized public OutputStream getOutputStream(String name) throws IOException
    {
        return new MyOutputStream(this, nameToConnection.get(name).getOutputStream());
    }

    synchronized public void addEntityToContext(Object newObject)
    {
        contextObjects.add(newObject);
    }

    synchronized public void startSnapshot()
    {
        System.out.println(">> Snapshot started. <<");
        SerializeObjects();
        SerializeConnections();
        savedMessages.clear();
        snapshotArrivedFrom.clear();
        for(String connectionName : nameToConnection.keySet())
            snapshotArrivedFrom.put(connectionName, false);
        snapshotting = true;

        byte[] snapshotMessage = new byte[MessageBuffer.snapshotMessage.length];
        for(int i=0; i<MessageBuffer.snapshotMessage.length; i++)
            snapshotMessage[i] = MessageBuffer.snapshotMessage[i];
        for(ConnectionManager c : connections)
        {
            try {
                c.getOutputStream().write(snapshotMessage);
            } catch (IOException e) { throw new RuntimeException("IOException"); }
        }
    }

    synchronized void snapshotMessageArrived(String connectionName)
    {
        //todo per Francio: controlla te lo abbiamo modificato
        snapshotArrivedFrom.replace(connectionName, true);
        boolean snapshotEndedFlag = true;
        for(Boolean arrived : snapshotArrivedFrom.values())
            snapshotEndedFlag = snapshotEndedFlag && snapshotting && arrived;
        if(snapshotEndedFlag)
            stopSnapshot();
    }

    synchronized void messageDuringSnapshot(String connectionName, ArrayList<Byte> message)
    {
        savedMessages.get(connectionName).addAll(message);
    }

    synchronized private void stopSnapshot()
    {
        snapshotting = false;
        notifyAll();
        SerializeMessages();
        SerializeConnections();
        System.out.println(">> Snapshot ended <<");
    }


    synchronized void waitUntilSnapshotEnded() throws InterruptedException
    {
        while (isSnapshotting())
            wait();
    }
    synchronized boolean isSnapshotting()
    {
        return snapshotting;
    }

    public void SerializeMessages(){
        Gson gson = new Gson();

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("Messages"+identifier+".json"));
            out.write(gson.toJson(savedMessages));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void SerializeObjects(){
        Gson gson = new Gson();
        String serializedObjects = gson.toJson(contextObjects);

        // Scrittura su file
        File file = new File("Objects" + identifier + ".json");
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(serializedObjects);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void SerializeConnections(){
        Gson gson = new Gson();
        ArrayList<String> conn = new ArrayList<>();
        for (ConnectionManager connectionManager :
                connections) {
            conn.add(connectionManager.getIp());
        }

        // Method for serialization of object
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("Connections"+identifier+".json"));
            out.write(gson.toJson(conn));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("Port"+identifier+".json"));
            out.write(gson.toJson(serverPort));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public Map<String, ConnectionManager> getNameToConnection() {
        return nameToConnection;
    }


    public String readMessages(){
        HashMap<String, ArrayList<Byte>> m = messages.getIncomingMessages();
        String s=null;
        for (String name: m.keySet()) {
            System.out.println(name + " :");
            ArrayList bytes = m.get(name);
            byte b[] = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++)
                b[i] = (byte) bytes.get(i);
            s = new String(b, StandardCharsets.UTF_8);
            System.out.println(s);
        }
        return s;
    }

    public List<ConnectionManager> getConnections() {
        return connections;
    }


}
