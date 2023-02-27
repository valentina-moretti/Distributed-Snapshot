package org.project;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class SnapshotCreator
{
    static final int serverPort=55831;
    private List<Serializable> savedObjects;
    private MessageBuffer messages;
    private Map<String, ConnectionManager> nameToConnection;
    private int numOfConnections;
    private List<ConnectionManager> connections;
    private ConnectionAccepter connectionAccepter;

    public SnapshotCreator(Serializable mainObject) throws IOException
    // there should be another parameter: the function to
    // be executed when reloading from a previous snapshot
    {
        savedObjects = new ArrayList<>();
        savedObjects.add(mainObject);
        messages = new MessageBuffer();
        nameToConnection = new HashMap<>();
        connections = new ArrayList<>();
        connectionAccepter = new ConnectionAccepter(this);
        numOfConnections = 0;
        connectionAccepter.start();
    }

    synchronized void connectionAccepted(Socket connection)
    {
        ConnectionManager newConnectionM = new ConnectionManager(messages, connection);
        connections.add(newConnectionM);
        numOfConnections++;
        String name = "Connection" + Integer.toString(numOfConnections);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
    }

    synchronized public String connect_to(InetAddress address) throws IOException
    {
        Socket socket = new Socket(address, serverPort);
        ConnectionManager newConnectionM = new ConnectionManager(socket);
        connections.add(newConnectionM);
        numOfConnections++;
        String name = "Connection" + Integer.toString(numOfConnections);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
        return name;
    }

    synchronized public Message receive(String name)
    {
        //popMessage
    }

    synchronized public void send(String name, Message )
    {

    }

    synchronized public void add_entity(Serializable newObject)
    {

    }
}
