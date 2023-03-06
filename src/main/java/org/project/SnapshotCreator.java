package org.project;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SnapshotCreator implements Serializable
{
    static final int serverPort=55831;
    private List<Serializable> contextObjects;
    private MessageBuffer messages;
    private Map<String, ConnectionManager> nameToConnection;
    private int numOfConnections;
    private List<ConnectionManager> connections;
    private ConnectionAccepter connectionAccepter;
    boolean snapshotting;
    Object snapshotLock;
    Map<String, List<Byte>> savedMessages;
    //Map<String, List<String>> channelClosed; // for each node, from what channel he has already received snap-message

    //todo: news da Valentina
    public SnapshotCreator(Serializable mainObject) throws IOException
    // there should be another parameter: the function to
    // be executed when reloading from a previous snapshot
    {
        File file=new File("savedState.txt");
        //if the file do not exist: is the first time I'm creating it
        if(file.length()==0){
            contextObjects = new ArrayList<>();
            contextObjects.add(mainObject);
            messages = new MessageBuffer();
            nameToConnection = new HashMap<>();
            connections = new ArrayList<>();
            connectionAccepter = new ConnectionAccepter(this);
            numOfConnections = 0;
            connectionAccepter.start();
            snapshotting = false;
        }
        else{
            //I'm recovering
            SnapshotCreator snapshotCreator_recovered = Deserialization();
            snapshotCreator_recovered.connectionAccepter.start();
        }

    }



    synchronized void connectionAccepted(Socket connection)
    {
        numOfConnections++;
        String name = "Connection" + Integer.toString(numOfConnections);
        ConnectionManager newConnectionM = new ConnectionManager(connection, name, messages, this);
        connections.add(newConnectionM);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
    }

    synchronized public String connect_to(InetAddress address) throws IOException
    {
        numOfConnections++;
        String name = "Connection" + Integer.toString(numOfConnections);
        Socket socket = new Socket(address, serverPort);
        ConnectionManager newConnectionM = new ConnectionManager(socket, name, messages, this);
        connections.add(newConnectionM);

        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
        return name;
    }

    synchronized public InputStream getInputStream(String name)
    {
        return messages.getInputStream(name);
    }

    synchronized public OutputStream getOutputStream(String name) throws IOException
    {
        return new MyOutputStream(this, nameToConnection.get(name).getOutputStream());
    }

    synchronized public void addEntityToContext(Serializable newObject)
    {
        contextObjects.add(newObject);
    }

    synchronized public void StartSnapshot(){
        synchronized (snapshotLock){
            while(snapshotting){
                try {
                    snapshotLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            snapshotting = true;
            SnapshotStarted();
            SaveState();

        }
    }

    synchronized void SnapshotStarted(){
        for (ConnectionManager connection :
                connections) {
            try {
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter( connection.getOutputStream() ));
                out.write((byte) 255);
                BufferedReader in = new BufferedReader(new InputStreamReader( connection.getInputStream() ));
                connection.SetSnapshotting();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        //send snapshot messages to everybody

    }

    synchronized void SetSnapshotting(){
        for (ConnectionManager c:connections) {
            c.SetSnapshotting();
        }
    }

    synchronized void StopSnapshot(){
        synchronized (snapshotLock){
            while(snapshotting){
                try {
                    snapshotLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            snapshotting = false;

        }
    }

    public void SaveState(){
        String filename = "savedState.txt";

        // Serialization
        try {

            // Saving of object in a file
            FileOutputStream file = new FileOutputStream
                    (filename);
            ObjectOutputStream out = new ObjectOutputStream
                    (file);

            // Method for serialization of object
            out.writeObject(this);

            out.close();
            file.close();

            System.out.println("Object has been serialized\n"
                    + "Data before Deserialization.");

        }

        catch (IOException ex) {
            System.out.println("IOException is caught");
        }
    }

    public SnapshotCreator Deserialization(){
        SnapshotCreator object = null;

        // Deserialization
        try {

            // Reading the object from a file
            FileInputStream file = new FileInputStream
                    ("savedState.txt");
            ObjectInputStream in = new ObjectInputStream
                    (file);

            // Method for deserialization of object
            object = (SnapshotCreator)in.readObject();

            in.close();
            file.close();
            System.out.println("Object has been deserialized\n"
                    + "Data after Deserialization.");
        }

        catch (IOException ex) {
            System.out.println("IOException is caught");
        }

        catch (ClassNotFoundException ex) {
            System.out.println("ClassNotFoundException" +
                    " is caught");
        }
        return object;
    }


    /*
    public List<String> getChannelClosed(String name) {
        return channelClosed.get(name);
    }

    public void setChannelClosed(String who, String whichChannel) {
        this.channelClosed.get(who).add(whichChannel);
    }
    */



    /*
    synchronized public List<Byte> readMessage(String name){
        ConnectionManager connectionManager = nameToConnection.get(name);
        MessageBuffer messageBuffer = connectionManager.getBuffer();
        return messageBuffer.retrieveMessage(name);
    }
    */
}
