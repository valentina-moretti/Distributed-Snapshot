package org.project;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class SnapshotCreator
{
    int reloadCount;
    private final int serverPort;
    private final List<Object> contextObjects;
    private transient ControllerInterface controller;
    private transient MessageBuffer messages;
    final HashSet<String> connectionNames;
    Map<String, Boolean> reloadConnections;
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
    public static SnapshotCreator snapshotDeserialization(int identifier, int serverPort, Map<String, Boolean> reloadConn) throws FileNotFoundException
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
        HashSet<String> oldConnections = new HashSet<>(recoveredSystem.getConnections());
        System.out.println("Connections to be reloaded: " + oldConnections);
        recoveredSystem.connectionNames.clear();
        recoveredSystem.connectionAccepter.start();

        HashSet<String> doNotReload = new HashSet<>();

        if (reloadConn == null) doNotReload = new HashSet<>();
        else{
            for(String c: reloadConn.keySet()){
                if(!reloadConn.get(c)) doNotReload.add(c);
            }
        }
        try {
            doNotReload.add(InetAddress.getLocalHost().toString().split("/")[1]+"-"+recoveredSystem.serverPort);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Reloading");
        recoveredSystem.reloadSnapshotMessage(oldConnections, doNotReload);

        try {
            System.out.println("Reconnecting");
            recoveredSystem.reconnect(oldConnections);
            System.out.println("Successfully reconnected to everyone.");
        } catch (IOException e) {
            e.printStackTrace();
        }



        new Thread(recoveredController).start();
        System.out.println("Recovered Controller is running.");
        recoveredSystem.reloadConnections = new HashMap<>();
        for(String conn : recoveredSystem.connectionNames){
            recoveredSystem.reloadConnections.put(conn, true);
        }
        recoveredSystem.reloadCount = 0;
        return recoveredSystem;
    }

    public int getServerPort(){
        return serverPort;
    }

    private void reloadSnapshotMessage(HashSet<String> oldConnections, HashSet<String> doNotReload)
    {
        System.out.println("Connections to be reloaded: " + oldConnections);
        System.out.println("Connections not to be reloaded: " + doNotReload);
        for (String c: doNotReload){
            oldConnections.remove(c);
        }
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

                //Writing reload message
                getOutputStream(connection).write(reloadMessage);

                Thread.sleep(1000);

                //Writing connections already reloaded
                PrintWriter out = new PrintWriter(getOutputStream(connection), true);
                /*

                String list = "";
                for(String c: doNotReload) list = list + c + ";";
                out.println(list);

                 */
                for(String c: doNotReload){
                    System.out.println("Sending already reloaded connection: " + c);
                    getOutputStream(connection).write(c.getBytes());
                    //Waiting for ack
                    Thread.sleep(3000);
                    System.out.println("Reading ack: ");
                    long startTime = System.currentTimeMillis();
                    long timeout = 6000;
                    while(getInputStream(connection).available() < 3 && (System.currentTimeMillis() - startTime) < timeout){
                        Thread.sleep(100);
                    }
                    BufferedInputStream bis = new BufferedInputStream(getInputStream(connection));
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    for (int ack = bis.read(); ack != -1; ack = bis.read()) {
                        buf.write((byte) ack);
                    }
                    String ack = buf.toString(StandardCharsets.UTF_8);
                    System.out.println(ack);
                    if(!ack.equals("ack")) throw new RuntimeException("Cannot get ack");
                }


                long startTime = System.currentTimeMillis();
                long timeout = 1000;
                while(getInputStream(connection).available() < reloadResponse.length && (System.currentTimeMillis() - startTime) < timeout){
                    Thread.sleep(100);
                }

                //Writing reload message again
                getOutputStream(connection).write(reloadMessage);

                System.out.println("Reload message, list of connections, and reload message again sent to " + name + ". Waiting for the reload response");
                startTime = System.currentTimeMillis();
                timeout = 10000;
                while(getInputStream(connection).available() < reloadResponse.length && (System.currentTimeMillis() - startTime) < timeout){
                    Thread.sleep(100);
                }
                int respLength = getInputStream(connection).read(reloadResponse, 0, reloadResponse.length);
                System.out.println("Reload response: " + Arrays.toString(reloadResponse));

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
        reloadCount = 0;
        this.serverPort = serverPort;
        SnapshotCreator.identifier = identifier;
        stopController = false;
        connectionNames = new HashSet<>();
        reloadConnections = new HashMap<>();
        for(String conn : connectionNames){
            reloadConnections.put(conn, true);
        }
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
            // Chi ha chiesto la connessione ha @timeout ms per mandare il proprio ip e porta del serversocket.
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
            System.out.println("Message: " + message);
            String[] parts = message.split("-");
            String clientAddress = parts[0];
            int clientPort = Integer.parseInt(parts[1]);
            name = clientAddress + "-" + clientPort;
            if(name.contains("/")) name = name.split("/")[1];
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
            reloadConnections.put(name, true);
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
        if(name.contains("/")) name = name.split("/")[1];
        Socket clientSocket = null;
        try {
            clientSocket = new Socket(address, port);
            ConnectionManager newConnectionM = new ConnectionManager(clientSocket, name, messages);
            connectionNames.add(name);
            reloadConnections.put(name, true);
            connections.add(newConnectionM);
            messages.addClient(name);
            nameToConnection.put(name, newConnectionM);
            newConnectionM.start();
            String message = clientSocket.getInetAddress().getHostAddress() + "-" + getServerPort();
            System.out.println("Sending my address and port: " + message);
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
        connectionNames.remove(connectionName);
    }

    synchronized public void reconnect(HashSet<String> connectionNames) throws IOException
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
                System.out.println(lastIp +" + " + port + " > " + my_address + " + " + serverPort + ": I have to reconnect");
                try {
                    connect_to(InetAddress.getByName(address), Integer.parseInt(port));
                } catch (ConnectException e){
                    System.out.println("Connection refused from " + name);
                }
            }
            else{
                System.out.println("Waiting for " + name + " to connect to me!");
                long startTime = System.currentTimeMillis();
                int timeout = 5000;
                while((System.currentTimeMillis() - startTime) < timeout && !connectionNames.contains(name)){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!connectionNames.contains(name)) throw new RuntimeException(name + "Is not trying to reconnect to me");
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

    public HashSet<String> getConnections() {
        return this.connectionNames;
    }

    void ReloadMessageArrived(String name) {
        System.out.println("Reload message arrived");
        reloadCount++;
        if(reloadCount == 1){
            System.out.println("Listening for already reloaded connections");
            // Listen for already reloaded connections
            InputStream in = getInputStream(name);
            OutputStream out = null;
            try {
                out = getOutputStream(name);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            BufferedInputStream bis = new BufferedInputStream(in);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            PrintWriter Pout = new PrintWriter(out, true);
            long startTime = System.currentTimeMillis();
            int timeout = 20000;
            while((System.currentTimeMillis() - startTime) < timeout && reloadCount == 1){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                try {
                    System.out.println(in.available());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    if(in.available() > 0) {
                        System.out.println("(Reload) Message arrived");

                        for (int message = bis.read(); message != -1; message = bis.read()) {
                            buf.write((byte) message);
                        }
                        String message = buf.toString(StandardCharsets.UTF_8);
                        System.out.println("Message: " + message);

                         /*

                        byte[] buffer = new byte[1024];
                        int bytesRead = 0;
                        try {
                            bytesRead = in.read(buffer);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        String message = new String(buffer, 0, bytesRead);

                          */


                        System.out.println("Already Reloaded Connection Received: " + message);

                        //Removing it from reloadConnections
                        reloadConnections.put(message, false);

                        String ack = "ack";
                        out.write(ack.getBytes());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            return;

            // Removing those connections from this.reloadConnections
            /*
            for (String conn : alreadyReloaded){
                if(reloadConnections.containsKey(conn)) reloadConnections.put(conn, false);
            }

             */


        }
        // Marking this connection as already reloaded
        try {
            reloadConnections.put(InetAddress.getLocalHost().toString().split("/")[1]+"-"+serverPort, false);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }


        // Sending reload response
        System.out.println("Reload message arrived");
        byte[] response = new byte[MessageBuffer.reloadSnapResp.length];
        for(int j=0; j<MessageBuffer.reloadSnapResp.length; j++)
            response[j] = MessageBuffer.reloadSnapResp[j];
        try {
            getOutputStream(name).write(response);
            System.out.println("Reload response written to " + name);
            long t = System.currentTimeMillis();
            while((System.currentTimeMillis() - t) < 7000) Thread.sleep(100);
        } catch (IOException e)
        { throw new RuntimeException("Failed to send the response for reloading the snapshot"); } catch (
                InterruptedException e) {
            throw new RuntimeException(e);
        }
        // Closing connections with everyone
        try {
            for(String conn: getConnections()){
                System.out.println("Closing connection with " + conn);
                closeConnection(conn);
            }
        } catch (IOException ignored) {}

        // Stopping controller
        stopController();

        // Closing Accepter
        try {
            closeAccepter();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Snapshot Deserialization, passing reloadConnections updated
        try {
            SnapshotCreator.snapshotDeserialization(SnapshotCreator.getIdentifier(), getServerPort(), reloadConnections);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
