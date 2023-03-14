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
    @Serial private static final long serialVersionUID = 1032L;
    static final int serverPort=55831;
    private final List<Serializable> contextObjects;
    private transient MessageBuffer messages;
    private transient Map<String, ConnectionManager> nameToConnection;
    private transient List<ConnectionManager> connections;
    private transient ConnectionAccepter connectionAccepter;
    private transient boolean snapshotting;
    private transient Map<String, Boolean> snapshotArrivedFrom;
    private transient Map<String, List<Byte>> savedMessages;

    //todo: news da Valentina
    public SnapshotCreator(Serializable mainObject) throws IOException
    // TODO: there should be another parameter: the function to
    //  be executed when reloading from a previous snapshot
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

        //TODO: controllo recovery (l'if di questo costruttore)
        /*
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
        */
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

    synchronized void saveState()
    {
        try {
            File snapshotFile = new File("lastSnapshot.txt");
            if(!snapshotFile.createNewFile())
            {
                if(!snapshotFile.delete())
                    throw new RuntimeException("Failed to create snapshotFile");
                if(!snapshotFile.createNewFile())
                    throw new RuntimeException("Failed to create snapshotFile");
            }
            FileOutputStream file = new FileOutputStream(snapshotFile);
            ObjectOutputStream fileOut = new ObjectOutputStream(file);

            fileOut.writeObject(this);

            fileOut.close();
            file.close();
        }catch (IOException e) { throw new RuntimeException("Error in creating snapshot file!"); }
    }

    public SnapshotCreator snapshotDeserialization()
    {
        SnapshotCreator sc = null;
    }

    @Serial
    private synchronized void writeObject(ObjectOutputStream oos) throws IOException
    {
        oos.defaultWriteObject();
        Map<String, InetAddress> nameToIP = new HashMap<>();
        for(String name: nameToConnection.keySet())
            nameToIP.put(name, nameToConnection.get(name).getInetAddress());
        oos.writeObject(nameToIP);
    }

    @Serial
    private synchronized void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException
    {
        ois.defaultReadObject();
        messages = new MessageBuffer(this);
        nameToConnection = new HashMap<>();
        connections = new ArrayList<>();
        connectionAccepter = new ConnectionAccepter(this);
        connectionAccepter.start();
        snapshotting = false;
        snapshotArrivedFrom = new HashMap<>();
        savedMessages = new HashMap<>();
        Map<String, InetAddress> nameToIP = (HashMap<String, InetAddress>) ois.readObject();
        for(String name: nameToIP.keySet())
        {
            Socket socket = new Socket(nameToIP.get(name), serverPort);
            ConnectionManager newConnectionM = new ConnectionManager(socket, name, messages);
            connections.add(newConnectionM);
            messages.addClient(name);
            nameToConnection.put(name, newConnectionM);
            newConnectionM.start();
        }
    }
}
