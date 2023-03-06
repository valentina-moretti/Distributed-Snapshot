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
    private boolean snapshotting;
    Map<String, List<Byte>> savedMessages;
    //Map<String, List<String>> channelClosed; // for each node, from what channel he has already received snap-message

    public SnapshotCreator(Serializable mainObject) throws IOException
    // there should be another parameter: the function to
    // be executed when reloading from a previous snapshot
    {
        contextObjects = new ArrayList<>();
        contextObjects.add(mainObject);
        messages = new MessageBuffer(this);
        nameToConnection = new HashMap<>();
        connections = new ArrayList<>();
        connectionAccepter = new ConnectionAccepter(this);
        numOfConnections = 0;
        connectionAccepter.start();
        snapshotting = false;
    }

    synchronized void connectionAccepted(Socket connection)
    {
        numOfConnections++;
        String name = "Connection" + Integer.toString(numOfConnections);
        ConnectionManager newConnectionM = new ConnectionManager(connection, name, messages);
        connections.add(newConnectionM);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
    }

    synchronized public String connect_to(InetAddress address) throws IOException
    {
        numOfConnections++;
        String name = "Connection" + Integer.toString(numOfConnections);
        Socket socket = new Socket(address, serverPort);
        ConnectionManager newConnectionM = new ConnectionManager(socket, name, messages);
        connections.add(newConnectionM);

        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
        return name;
    }

    synchronized public InputStream getInputStream(String name)
    {
        return new MyInputStream(messages, name);
    }

    synchronized public OutputStream getOutputStream(String name) throws IOException
    {
        return new MyOutputStream(this, nameToConnection.get(name).getOutputStream());
    }

    synchronized public void addEntityToContext(Serializable newObject)
    {
        contextObjects.add(newObject);
    }

    synchronized public void startSnapshot(){
        synchronized (snapshotLock){
            while(snapshotting){
                try {
                    snapshotLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            snapshotting = true;
            snapshotStarted();
            saveState();

        }
    }

    synchronized void snapshotStarted(){
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

    synchronized void setSnapshotting(){
        for (ConnectionManager c:connections) {
            c.SetSnapshotting();
        }
    }

    synchronized void stopSnapshot(){
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

    boolean isSnapshotting()
    {
        return snapshotting;
    }











    public void saveState(){
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

    public void deserialization(String filename){
        SnapshotCreator object = null;

        // Deserialization
        try {

            // Reading the object from a file
            FileInputStream file = new FileInputStream
                    (filename);
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
    }
}
