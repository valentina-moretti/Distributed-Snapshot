package org.project;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class SnapshotCreator
{
    private static int serverPort;
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
    private boolean stopController;

    /**
     * @return a SnapshotCreator object reconstructed from the file named "lastSnapshot"
     * created during the last snapshot
     * @throws FileNotFoundException if the file "lastSnapshot" where the information about the latest
     * snapshot was not found or was corrupted
     */
    public static SnapshotCreator snapshotDeserialization(int identifier, boolean reloading) throws FileNotFoundException
    {
        SnapshotCreator recoveredSystem = null;
        Map<String, ArrayList<Byte>> messages = null;
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

        ControllerInterface recoveredController = ControllerInterface.Deserialize(identifier);

        recoveredController.SetSnapshotCreator(recoveredSystem);
        recoveredSystem.controller = recoveredController;
        recoveredSystem.messages = new MessageBuffer(recoveredSystem);
        recoveredSystem.nameToConnection = new HashMap<>();
        recoveredSystem.connections = new ArrayList<>();
        recoveredSystem.snapshotArrivedFrom = new HashMap<>();
        SnapshotCreator.identifier = identifier;
        try {
            recoveredSystem.connectionAccepter = new ConnectionAccepter(recoveredSystem);
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> oldConnections = recoveredSystem.connectionNames.stream().toList();
        recoveredSystem.connectionNames.clear();
        recoveredSystem.connectionAccepter.start();
        if(!reloading) {
            System.out.println("Reloading");
            recoveredSystem.reloadSnapshotMessage(oldConnections);
        }
        try {
            System.out.println("Reconnecting");
            recoveredSystem.reconnect(oldConnections);
            System.out.println("Successfully reconnected to everyone.");
        } catch (IOException e) {
            e.printStackTrace();
        }



        new Thread(recoveredController).start();
        System.out.println("Recovered Controller is running.");
        return recoveredSystem;
    }

    public static int getServerPort(){
        return serverPort;
    }

    private void reloadSnapshotMessage(List<String> oldConnections)
    {
        System.out.println("Connections to be reloaded: " + oldConnections);
        for(String name : oldConnections)
        {
            System.out.println("Reloading " + name);
            if(name.contains("/")) name = name.split("/")[1];
            String[] strings=name.split("-");
            byte[] reloadMessage = new byte[MessageBuffer.reloadSnapMessage.length];
            byte[] reloadResponse = new byte[MessageBuffer.reloadSnapResp.length];
            try {
                System.out.println("Trying to connect to: " + strings[0] + "-" + strings[1]);
                String connection = connect_to(InetAddress.getByName(strings[0]), Integer.parseInt(strings[1]));

                for(int i=0; i<MessageBuffer.reloadSnapMessage.length; i++)
                    reloadMessage[i] = MessageBuffer.reloadSnapMessage[i];
                getOutputStream(connection).write(reloadMessage);
                long startTime = System.currentTimeMillis();
                int timeout = 5000;
                while(getInputStream(connection).available() < reloadResponse.length && (System.currentTimeMillis() - startTime) < timeout){
                    Thread.sleep(100);
                }
                int respLength = getInputStream(connection).read(reloadResponse, 0, reloadResponse.length);
                System.out.println("Reload message: " + Arrays.toString(reloadResponse));

                for(int i=0; i<reloadResponse.length; i++)
                {
                    if(reloadResponse[i]!=MessageBuffer.reloadSnapResp[i])
                        throw new RuntimeException("Connection Failed, the return message was malformed");
                }
                try{
                    System.out.println("Reload ok, closing connection with " + connection);
                    closeConnection(connection); } catch (IOException e) {e.printStackTrace();}
            } catch (IOException e){
                e.printStackTrace();
                throw new RuntimeException(e);
            } catch (
                    InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
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
    {
        SnapshotCreator.serverPort = serverPort;
        SnapshotCreator.identifier = identifier;
        stopController = false;
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
        this.controller = controller;

    }

    void closeAccepter() throws IOException {
        connectionAccepter.closeServerSocket();
    }


    /**
     * method used by the ConnectionAccepter when a new connection is accepted by the ServerSocket
     * @param connection new Socket
     */
    synchronized void connectionAccepted(Socket connection)
    {
        String name;
        InputStream inputStream;
        try {
            // Chi ha chiesto la connessione ha @tiemout ms per mandare il proprio ip e porta del serversocket.
            // Ne ho bisogno per la riconnessione
            long startTime = System.currentTimeMillis();
            int timeout = 1000;
            inputStream = connection.getInputStream();
            System.out.println("Available: " + inputStream.available());
            while((System.currentTimeMillis() - startTime) < timeout){
                Thread.sleep(100);
            }
            System.out.println("Available: " + inputStream.available());
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            String message = new String(buffer, 0, bytesRead);
            System.out.println(message);
            String[] parts = message.split("-");
            String clientAddress = parts[0];
            int clientPort = Integer.parseInt(parts[1]);
            name = clientAddress + "-" + clientPort;
            /*
            Socket clientSocket;
            try{
                clientSocket = new Socket(clientAddress, clientPort);
            } catch (UnknownHostException | SecurityException e){
                e.printStackTrace();
                return;
            }
            */
            String ack = "ack";
            connection.getOutputStream().write(ack.getBytes());
            ConnectionManager newConnectionM = new ConnectionManager(connection, name, messages);
            connectionNames.add(name);
            connections.add(newConnectionM);
            messages.addClient(name);
            nameToConnection.put(name, newConnectionM);
            newConnectionM.start();
            System.out.println("Successfully connected to " + name);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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
        Socket clientSocket = null;
        try {
            clientSocket = new Socket(address, port);
            ConnectionManager newConnectionM = new ConnectionManager(clientSocket, name, messages);
            connectionNames.add(name);
            connections.add(newConnectionM);
            messages.addClient(name);
            nameToConnection.put(name, newConnectionM);
            newConnectionM.start();
            String message = clientSocket.getInetAddress().getHostAddress() + "-" + getServerPort();
            System.out.println(message);
            getOutputStream(name).write(message.getBytes());

            //Waiting for ack
            InputStream inputStream = getInputStream(name);
            long startTime = System.currentTimeMillis();
            int timeout = 2500;
            while((System.currentTimeMillis() - startTime) < timeout){
                Thread.sleep(100);
            }
            System.out.println("Reading ack: ");
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            for (int ack = bis.read(); ack != -1; ack = bis.read()) {
                buf.write((byte) ack);
            }
            String ack = buf.toString(StandardCharsets.UTF_8);
            System.out.println(ack);
            if (ack.equals("ack")){
                System.out.println("Successfully connected to " + name);
                return name;
            }
            else {
                closeConnection(name);
                System.out.println("ACK not received from " + name + ", closing the connection");
                return null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized public void closeConnection(String connectionName) throws IOException
    {
        nameToConnection.get(connectionName).close();
    }

    synchronized public void reconnect(List<String> connectionNames) throws IOException
    {
        List<String> oldConnections = connectionNames.stream().toList();
        String address;
        String port;
        Socket socket = null;
        String[] strings;
        String my_address=InetAddress.getLocalHost().toString().split("/")[1].split("\\.")[3];
        System.out.println("I have to reconnect to: " + oldConnections);
        for(String name: oldConnections){
            System.out.println("Reconnecting to: " + name);
            if (name.contains("/")) name = name.split("/")[1];
            strings=name.split("-");
            address=strings[0];
            String lastIp = address.split("\\.")[3];
            port=strings[1];
            if(Integer.parseInt(lastIp+port)>Integer.parseInt(my_address+serverPort)) {
                System.out.println(lastIp +" + " + port + " > " + my_address + " + " + serverPort);
                try {
                    connect_to(InetAddress.getByName(address), Integer.parseInt(port));
                } catch (ConnectException e){
                    System.out.println("Connection refused from " + name);
                }
            }
            else{
                System.out.println("Waiting for " + name + " to connect to me!");
            }
        }
    }

    void stopController() {
        this.stopController = true;
    }

    public boolean ControllerHasToStop() {
        return stopController;
    }

    public static int getIdentifier() {
        return identifier;
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
        controller.Serialize();
    }

    public List<String> getConnections() {
        return this.connectionNames;
    }
}
