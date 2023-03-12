package org.project;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;


public class SnapshotCreator
{
    static int serverPort;
    //todo:non usiamo piu i serializable
    private ArrayList<Object> contextObjects;
    private MessageBuffer messages;
    private List<String> connectionNames;
    private transient Map<String, ConnectionManager> nameToConnection;
    private transient List<ConnectionManager> connections;
    private transient ConnectionAccepter connectionAccepter;
    private transient JsonConverter jsonConverter;
    private boolean snapshotting;
    private transient Map<String, Boolean> snapshotArrivedFrom;
    private Map<String, List<Byte>> savedMessages;

    public SnapshotCreator(Object mainObject, int serverPort) throws IOException
    // there should be another parameter: the function to
    // be executed when reloading from a previous snapshot
    {
        snapshotting = false;
        snapshotArrivedFrom = new HashMap<>();
        jsonConverter= new JsonConverter();
        messages = new MessageBuffer(this);
        nameToConnection = new HashMap<>();
        connections = new ArrayList<>();
        connectionNames = new ArrayList<>();
        contextObjects = new ArrayList<>();
        savedMessages = new HashMap<>();

        File file=new File("Objects.json");
        //if the file do not exist: is the first time I'm creating it
        if(file.length()==0)
        {
            SnapshotCreator.serverPort = serverPort;
            contextObjects.add(mainObject);
        }
        else {
            //I'm recovering
            System.out.println("Recovering.");
            Recover();
            System.out.println("Recovering completed.");

        }
        connectionAccepter = new ConnectionAccepter(this);
        connectionAccepter.start();


    }

    void Recover() throws IOException {
        Gson gson = new Gson();

        //Port
        BufferedReader in = new BufferedReader(new FileReader("Port.json"));
        SnapshotCreator.serverPort = gson.fromJson(in, Integer.class);


        //Objects
        in = new BufferedReader(new FileReader("Objects.json"));
        this.contextObjects = gson.fromJson(in, new TypeToken<ArrayList<Object>>(){}.getType());

        //Connections
        in = new BufferedReader(new FileReader("Connections.json"));
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
        in = new BufferedReader(new FileReader("Messages.json"));
        this.savedMessages = gson.fromJson(in, new TypeToken<Map<String, List<Byte>>>(){}.getType());

    }


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
        saveState();
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

    synchronized void messageDuringSnapshot(String connectionName, List<Byte> message)
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
            BufferedWriter out = new BufferedWriter(new FileWriter("Messages.json"));
            out.write(gson.toJson(savedMessages));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void SerializeObjects(){
        Gson gson = new Gson();

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("Objects.json"));
            out.write(gson.toJson(contextObjects));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
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
            BufferedWriter out = new BufferedWriter(new FileWriter("Connections.json"));
            out.write(gson.toJson(conn));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("Port.json"));
            out.write(gson.toJson(serverPort));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void saveState(){
        String filename = "SnapCreator.json";
        String saveObjects = "Objects.json";


        Gson gson = new Gson();

        // Serialization
        try {

            // Saving of SnapCreator in a file
            BufferedWriter out = new BufferedWriter(new FileWriter("SnapCreator.json"));

            // Method for serialization of object
            out.write(jsonConverter.fromObjectToJson(this));



            out.close();

            System.out.println("Object has been serialized\n");

        }

        catch (IOException ex) {
            System.out.println("IOException is caught");
        }
    }

    public SnapshotCreator snapshotDeserialization(){
        SnapshotCreator sc = null;

        // Deserialization


            // Method for deserialization of object
            sc = jsonConverter.fromJsonFileToObject("SnapCreator.json");
            System.out.println("Object has been deserialized\n");


        return sc;
    }

    public Map<String, ConnectionManager> getNameToConnection() {
        return nameToConnection;
    }


    public void readMessages(){
        //for (Map.Entry<String, List<Byte>> entry : messages.getIncomingMessages().entrySet()) {
            //            System.out.println(entry.getKey() + ":" + entry.getValue());
        //}
            System.out.println(messages.getIncomingMessages());
    }

    public List<ConnectionManager> getConnections() {
        return connections;
    }


}
