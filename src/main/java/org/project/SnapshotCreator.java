package org.project;

import com.google.gson.Gson;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SnapshotCreator
{
    static final int serverPort=55831;
    //todo:non usiamo piu i serializable
    private List<Serializable> contextObjects;
    private MessageBuffer messages;
    private Map<String, ConnectionManager> nameToConnection;
    private List<ConnectionManager> connections;
    private ConnectionAccepter connectionAccepter;
    private JsonConverter jsonConverter;
    private boolean snapshotting;
    private Map<String, Boolean> snapshotArrivedFrom;
    private Map<String, List<Byte>> savedMessages;

    //todo: news da Valentina
    public SnapshotCreator(Serializable mainObject) throws IOException
    // there should be another parameter: the function to
    // be executed when reloading from a previous snapshot
    {
        File file=new File("SnapCreator.txt");
        //if the file do not exist: is the first time I'm creating it
        if(file.length()==0)
        {
            contextObjects = new ArrayList<>();
            contextObjects.add(mainObject);
            messages = new MessageBuffer(this);
            nameToConnection = new HashMap<>();
            connections = new ArrayList<>();
            connectionAccepter = new ConnectionAccepter(this);
            connectionAccepter.start();
            snapshotting = false;
            snapshotArrivedFrom = new HashMap<>();
            savedMessages = new HashMap<>();
            jsonConverter= new JsonConverter();
        }
        else
        {
            //I'm recovering
            SnapshotCreator snapshotCreator_recovered = snapshotDeserialization();
            snapshotCreator_recovered.connectionAccepter.start();
            this.connections = snapshotCreator_recovered.connections;
            this.savedMessages = snapshotCreator_recovered.savedMessages;
            this.nameToConnection = snapshotCreator_recovered.nameToConnection;

        }

    }


    synchronized void connectionAccepted(Socket connection)
    {
        String name = connection.getInetAddress().toString();
        ConnectionManager newConnectionM = new ConnectionManager(connection, name, messages);
        connections.add(newConnectionM);
        messages.addClient(name);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
    }

    synchronized public String connect_to(InetAddress address) throws IOException
    {
        String name = address.toString();
        Socket socket = new Socket(address, serverPort);
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

    synchronized public void addEntityToContext(Serializable newObject)
    {
        contextObjects.add(newObject);
    }

    synchronized void startSnapshot()
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
        snapshotArrivedFrom.replace(connectionName, true);
        boolean snapshotEndedFlag = false;
        for(Boolean arrived : snapshotArrivedFrom.values())
            snapshotEndedFlag = snapshotting && arrived;
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
        //TODO: salvo tutti i messaggi e lo stato nello stesso file
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

    void SerializeMessages(){
        Gson gson = new Gson();

        // Method for serialization of object
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("Messages.txt"));
            out.write(gson.toJson(savedMessages));
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void saveState(){
        String filename = "SnapCreator.txt";
        String saveObjects = "Objects.txt";


        Gson gson = new Gson();

        // Serialization
        try {

            // Saving of SnapCreator in a file
            BufferedWriter out = new BufferedWriter(new FileWriter("SnapCreator.txt"));

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
            sc = jsonConverter.fromJsonFileToObject("SnapCreator.txt");
            System.out.println("Object has been deserialized\n");


        return sc;
    }

    public Map<String, ConnectionManager> getNameToConnection() {
        return nameToConnection;
    }

    public Map<String, List<Byte>> getIncomingMessages(ConnectionManager connectionManager) {
        return connectionManager.getBuffer().getIncomingMessages();
    }

    public Map<String, Map<String, List<Byte>>> readMessages(){
        Map<String, Map<String, List<Byte>>> messages = new HashMap<>();
        for (ConnectionManager connectionManager: connections){
            messages.put(connectionManager.getIp(), getIncomingMessages(connectionManager));
        }
        return messages;
    }

    public List<ConnectionManager> getConnections() {
        return connections;
    }


}
