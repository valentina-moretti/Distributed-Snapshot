package org.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SnapshotCreator
{
    static final int serverPort=55831;
    private List<Serializable> contextObjects;
    private MessageBuffer messages;
    private Map<String, ConnectionManager> nameToConnection;
    private int numOfConnections;
    private List<ConnectionManager> connections;
    private ConnectionAccepter connectionAccepter;

    public SnapshotCreator(Serializable mainObject) throws IOException
    // there should be another parameter: the function to
    // be executed when reloading from a previous snapshot
    {
        contextObjects = new ArrayList<>();
        contextObjects.add(mainObject);
        messages = new MessageBuffer();
        nameToConnection = new HashMap<>();
        connections = new ArrayList<>();
        connectionAccepter = new ConnectionAccepter(this);
        numOfConnections = 0;
        connectionAccepter.start();
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
        return messages.getInputStream(name);
    }

    synchronized public OutputStream getOutputStream(String name) throws IOException
    {
        return nameToConnection.get(name).getOutputStream();
    }

    synchronized public void addEntityToContext(Serializable newObject)
    {
        contextObjects.add(newObject);
    }

    synchronized public Message readMessage(String name){
        ConnectionManager connectionManager = nameToConnection.get(name);
        MessageBuffer messageBuffer = connectionManager.getBuffer();
        return messageBuffer.retreiveMessage(name);
    }
}
