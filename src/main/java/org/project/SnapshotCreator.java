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

    /**
     * @return a SnapshotCreator object reconstructed from the file named "lastSnapshot"
     * created during the last snapshot
     * @throws FileNotFoundException if the file "lastSnapshot" where the information about the latest
     * snapshot was not found or was corrupted
     */
    static public SnapshotCreator snapshotDeserialization() throws FileNotFoundException
    {
        SnapshotCreator recoveredSystem = null;
        Map<String, List<Byte>> messages = null;
        //TODO: dovrei eseguire il metodo/i metodi che l'applicazione mi ha passato per riavviarla
        try{
            File messagesFile = new File("savedMessages");
            FileInputStream file = new FileInputStream(messagesFile);
            ObjectInputStream fileIn = new ObjectInputStream(file);

            Object inObj = fileIn.readObject();
            if(inObj instanceof Map)
                messages = (Map<String, List<Byte>>) inObj;
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
        synchronized (recoveredSystem) { recoveredSystem.savedMessages = messages; }
        return recoveredSystem;
    }

    /**
     * Constructor of the SnapshotCreator, which will add the main object to the context of the snapshot
     * (only the objects added to the context will be saved in the state of the program during the snapshot,
     * see method addEntityToContext)
     * @param mainObject the main object of your program (the one which is most connected to the other objects)
     * @throws IOException
     */
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
    }


    /**
     * method used by the ConnectionAccepter when a new connection is accepted by the ServerSocket
     * @param connection new Socket
     */
    synchronized void connectionAccepted(Socket connection)
    {
        String name = connection.getInetAddress().toString();
        ConnectionManager newConnectionM = new ConnectionManager(connection, name, messages);
        connections.add(newConnectionM);
        messages.addClient(name);
        nameToConnection.put(name, newConnectionM);
        newConnectionM.start();
    }

    /**
     * Use this method to connect to other nodes knowing their address; if a new connection is established
     * without the usage of this method the communication of that connection will not be registered in the
     * snapshot
     * @param address
     * @return a String identifier of the connection created
     * @throws IOException
     */
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

    /**
     * getter for the input stream of a specific connection
     * @param connectionName string identifier of the connection
     * @return the input stream
     */
    synchronized public InputStream getInputStream(String connectionName)
    {
        return new MyInputStream(messages, connectionName);
    }

    /**
     * getter for the output stream of a specific connection
     * @param connectionName string identifier of the connection
     * @return the output stream
     * @throws IOException
     */
    synchronized public OutputStream getOutputStream(String connectionName) throws IOException
    {
        return new MyOutputStream(this, nameToConnection.get(connectionName).getOutputStream());
    }

    /**
     * add a serializable object to the context of the snapshot.
     * Only the objects added to the context will be saved in the state of the program during the snapshot
     * @param newObject
     */
    synchronized public void addEntityToContext(Serializable newObject)
    {
        contextObjects.add(newObject);
    }

    /**
     * Begin the snapshot by saving the state and sending the snapshot message to all other nodes
     */
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

    /**
     * Tells the SnapshotCreator that the snapshot message arrived from the connection connectionName.
     * If the snapshot messages arrived from all the connections the snapshot logic will end
     * @param connectionName string identifier of the connection
     */
    synchronized void snapshotMessageArrived(String connectionName)
    {
        snapshotArrivedFrom.replace(connectionName, true);
        boolean snapshotEndedFlag = false;
        for(Boolean arrived : snapshotArrivedFrom.values())
            snapshotEndedFlag = snapshotting && arrived;
        if(snapshotEndedFlag)
            stopSnapshot();
    }

    /**
     * Method used to save all the messages arrived during the snapshot, from a specific connection, as a
     * list of bytes
     * @param connectionName string identifier of the connection
     * @param message
     */
    synchronized void messageDuringSnapshot(String connectionName, List<Byte> message)
    {
        savedMessages.get(connectionName).addAll(message);
    }

    /**
     * As the state of the program was saved at the beginning of the snapshot, the messages arrived during
     * the snapshot are saved as well at the end of the snapshot
     */
    synchronized private void stopSnapshot()
    {
        snapshotting = false;
        notifyAll();
        try {
            File messagesFile = new File("savedMessages");
            if(!messagesFile.createNewFile())
            {
                if(!messagesFile.delete())
                    throw new RuntimeException("Failed to create savedMessages file");
                if(!messagesFile.createNewFile())
                    throw new RuntimeException("Failed to create savedMessages file");
            }
            FileOutputStream file = new FileOutputStream(messagesFile);
            ObjectOutputStream fileOut = new ObjectOutputStream(file);

            fileOut.writeObject(savedMessages);

            fileOut.close();
            file.close();
        }catch (IOException e) { throw new RuntimeException("Error in creating savedMessages file!"); }
        savedMessages.clear();
    }

    /**
     * Suspends the calling thread as long as there is a snapshot running
     * @throws InterruptedException
     */
    synchronized void waitUntilSnapshotEnded() throws InterruptedException
    {
        while (isSnapshotting())
            wait();
    }

    synchronized boolean isSnapshotting()
    {
        return snapshotting;
    }

    synchronized private void saveState()
    {
        try {
            File snapshotFile = new File("lastSnapshot");
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
        //TODO: devo riconnettermi solo a quelli con cui non ho già una connessione e rifiutare o chiudere
        // le connessioni da quelli con cui mi sono già connesso
    }
}
