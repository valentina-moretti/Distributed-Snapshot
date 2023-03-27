package org.project;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;


public class SnapshotCreator
{
    static int serverPort;
    private List<Object> contextObjects;
    private transient ControllerInterface controller;
    private transient MessageBuffer messages;
    private List<String> connectionNames;
    private transient Map<String, ConnectionManager> nameToConnection;
    private transient List<ConnectionManager> connections;
    private transient ConnectionAccepter connectionAccepter;
    private boolean snapshotting;
    private Map<String, Boolean> snapshotArrivedFrom;
    private transient Map<String, ArrayList<Byte>> savedMessages;
    static int identifier;

    /**
     * @return a SnapshotCreator object reconstructed from the file named "lastSnapshot"
     * created during the last snapshot
     * @throws FileNotFoundException if the file "lastSnapshot" where the information about the latest
     * snapshot was not found or was corrupted
     */
    static public SnapshotCreator snapshotDeserialization(int identifier) throws FileNotFoundException
    {
        SnapshotCreator recoveredSystem = null;
        Map<String, ArrayList<Byte>> messages = null;
        //TODO: dovrei eseguire il metodo/i metodi che l'applicazione mi ha passato per riavviarla
        // i messaggi salvati li devo mettere nel buffer non in savedMessages
        // se non ci sono i file lancio FileNotFountException
        try{

            File messagesFile = new File("savedMessages"+identifier+".txt");
            Reader reader = new FileReader(messagesFile);

            Object inObj = null;
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, ArrayList<Byte>>>(){}.getType();
            inObj = gson.fromJson(reader, type);
            if(inObj instanceof Map)
                messages = (Map<String, ArrayList<Byte>>) inObj;
            else {
                System.out.println("Saved messages file was corrupted");
                throw new ClassNotFoundException("Saved messages file was corrupted");
            }


            File objectsFile = new File("lastSnapshot"+identifier+".txt");
            reader = new FileReader(objectsFile);
            recoveredSystem = gson.fromJson(reader, SnapshotCreator.class);

        }catch (IOException | ClassNotFoundException e) {
            //e.printStackTrace();
            throw new FileNotFoundException("File was corrupted");
        }
        synchronized (Objects.requireNonNull(recoveredSystem)) { recoveredSystem.savedMessages = messages; };

        ControllerInterface recoveredController = recoveredSystem.controller.Deserialize();

        recoveredController.SetSnapshotCreator(recoveredSystem);
        recoveredSystem.messages = new MessageBuffer(recoveredSystem);
        recoveredSystem.nameToConnection = new HashMap<>();
        recoveredSystem.connections = new ArrayList<>();
        try {
            recoveredSystem.reconnect(recoveredSystem.connectionNames);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            recoveredSystem.connectionAccepter = new ConnectionAccepter(recoveredSystem);
        } catch (IOException e) {
            e.printStackTrace();
        }
        recoveredSystem.connectionAccepter.start();
        recoveredSystem.snapshotArrivedFrom = new HashMap<>();
        SnapshotCreator.identifier = identifier;

        new Thread(recoveredController).start();
        System.out.println("Recovered Controller is running.");
        return recoveredSystem;
    }


    /**
     * Constructor of the SnapshotCreator, which will add the controller to the context of the snapshot
     * (only the objects added to the context will be saved in the state of the program during the snapshot,
     * see method addEntityToContext)
     * @param controller the main object of your program (the one which is most connected to the other objects),
     *                   it must be a thread in order to be executed after the recovery
     * @throws IOException
     */
    public SnapshotCreator(ControllerInterface controller, int identifier, int serverPort) throws IOException
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
        savedMessages = new HashMap<>();
        SnapshotCreator.serverPort = serverPort;
        SnapshotCreator.identifier = identifier;
        this.controller = controller;

    }


    /**
     * method used by the ConnectionAccepter when a new connection is accepted by the ServerSocket
     * @param connection new Socket
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

    /**
     * Use this method to connect to other nodes knowing their address; if a new connection is established
     * without the usage of this method the communication of that connection will not be registered in the
     * snapshot
     * @param address
     * @return a String identifier of the connection created
     * @throws IOException
     */
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

    synchronized public void reconnect(List<String> connectionNames) throws IOException
    {
        String address;
        String port;
        Socket socket;
        String[] strings;
        String my_address=InetAddress.getLocalHost().toString();

        ConnectionManager newConnectionM;
        for(String name: connectionNames){
            strings=name.split("-");
            address=strings[0];
            port=strings[1];
            if(Integer.valueOf(address+port)>Integer.valueOf(my_address+serverPort)) {
                System.out.println(address.substring(1));
                System.out.println(port);
                System.out.println(InetAddress.getByName(address.substring(1)));
                System.out.println(Integer.parseInt(port));
                socket = new Socket(InetAddress.getByName(address.substring(1)), Integer.parseInt(port));
                connectionNames.add(name);
                newConnectionM = new ConnectionManager(socket, name, messages);
                connections.add(newConnectionM);
                messages.addClient(name);
                nameToConnection.put(name, newConnectionM);
                newConnectionM.start();
            }
        }
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
    synchronized public void addEntityToContext(Object newObject)
    {
        contextObjects.add(newObject);
    }

    /**
     * Begin the snapshot by saving the state and sending the snapshot message to all other nodes
     */
    public synchronized void startSnapshot()
    {
        System.out.println("Starting snapshot");
        saveState();
        controller.Serialize();
        savedMessages.clear();
        snapshotArrivedFrom.clear();
        for(String connectionName : connectionNames){
            savedMessages.put(connectionName, new ArrayList<>());
            snapshotArrivedFrom.put(connectionName, false);
        }
        snapshotting = true;

        byte[] snapshotMessage = new byte[MessageBuffer.snapshotMessage.length];
        for(int i=0; i<MessageBuffer.snapshotMessage.length; i++)
            snapshotMessage[i] = MessageBuffer.snapshotMessage[i];
        for(ConnectionManager c : connections)
        {
            try {
                c.getOutputStream().write(snapshotMessage);
            } catch (IOException e) { System.out.println("The other one has disconnected," +
                    "can't send snapshot message to him"); }
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
        System.out.println(snapshotArrivedFrom);
        boolean snapshotEndedFlag = snapshotting;
        for(Boolean arrived : snapshotArrivedFrom.values())
            snapshotEndedFlag = snapshotEndedFlag && arrived;
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
        System.out.println("Saved messages: ");
        System.out.println(savedMessages);
    }

    /**
     * As the state of the program was saved at the beginning of the snapshot, the messages arrived during
     * the snapshot are saved as well at the end of the snapshot
     */
    synchronized private void stopSnapshot()
    {
        System.out.println("Stopping snapshot");
        snapshotting = false;
        notifyAll();

        Gson gson = new Gson();
        String serializedObjects = gson.toJson(savedMessages);
        File file = new File("savedMessages"+identifier+".txt");
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos);
             BufferedWriter writer = new BufferedWriter(osw))
        {
            writer.write(serializedObjects);
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            fileNotFoundException.printStackTrace();
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
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

    synchronized public void saveState()
    {
        Gson gson = new Gson();
        String serializedObjects = gson.toJson(this);
        File file = new File("lastSnapshot"+identifier+".txt");
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos);
             BufferedWriter writer = new BufferedWriter(osw))
        {
            writer.write(serializedObjects);
        } catch (IOException fileNotFoundException)
        {
            fileNotFoundException.printStackTrace();
        }
    }

    public List<String> getConnections() {
        return this.connectionNames;
    }

/*
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

 */
}
