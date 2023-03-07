package org.project;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.application.Controller;

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
    private List<Serializable> contextObjects; //
    private MessageBuffer messages;
    private Map<String, ConnectionManager> nameToConnection;
    private int numOfConnections;
    private List<ConnectionManager> connections; //
    private ConnectionAccepter connectionAccepter;
    private JsonConverter jsonConverter;
    private boolean snapshotting;
    Map<String, List<Byte>> savedMessages;

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
            numOfConnections = 0;
            connectionAccepter.start();
            snapshotting = false;
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

    //todo: errori: non sono cose che ha connection
    synchronized void snapshotStarted(){
        for (ConnectionManager connection :
                connections) {
            try {
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter( connection.getOutputStream() ));
                out.write((byte) 255);
                BufferedReader in = new BufferedReader(new InputStreamReader( connection.getInputStream() ));
                connection.setSnapshotting();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        //send snapshot messages to everybody

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

    // TODO: questi metodi non dovrebbbero esserci
    /*
    public List<ConnectionManager> getConnections() {
        return connections;
    }

    public Map<String, List<Byte>> getSavedMessages() {
        return savedMessages;
    }

    public List<Serializable> getContextObjects() {
        return contextObjects;
    }

    public void setConnections(List<ConnectionManager> connections) {
        this.connections = connections;
    }

    public void setSavedMessages(Map<String, List<Byte>> savedMessages) {
        this.savedMessages = savedMessages;
    }

    public void setContextObjects(List<Serializable> contextObjects) {
        this.contextObjects = contextObjects;
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
