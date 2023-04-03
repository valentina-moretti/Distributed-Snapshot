package org.project;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

class MessageBuffer
{
    static final Byte[] snapshotMessage =
            {(byte)255, (byte)255, (byte)255, (byte)255, (byte)112, (byte)255, (byte)255, (byte)255, (byte)255,
             (byte)255, (byte)255, (byte)255, (byte)255, (byte)112, (byte)255, (byte)255, (byte)255, (byte)255};
    static final Byte[] reloadSnapMessage =
            {(byte)255, (byte)255, (byte)255, (byte)255, (byte)115, (byte)255, (byte)255, (byte)255, (byte)255,
             (byte)255, (byte)255, (byte)255, (byte)255, (byte)115, (byte)255, (byte)255, (byte)255, (byte)255};
    static final Byte[] reloadSnapResp =
            {(byte)255, (byte)255, (byte)255, (byte)255, (byte)118, (byte)255, (byte)255, (byte)255, (byte)255,
             (byte)255, (byte)255, (byte)255, (byte)255, (byte)118, (byte)255, (byte)255, (byte)255, (byte)255};
    private final Map<String, List<Byte>> incomingMessages;
    private transient final SnapshotCreator snapshotManager;

    MessageBuffer(SnapshotCreator snapshotCreator)
    {
        incomingMessages = new HashMap<>();
        snapshotManager = snapshotCreator;
    }

    /**
     * Used to add a connection queue of messages to the MessageBuffer
     * @param name string identifier of the connection
     */
    synchronized void addClient(String name)
    {
        incomingMessages.put(name, new ArrayList<>());
    }

    /**
     * add a List of Bytes arrived from a specific connection to the message buffer
     * @param name identifier of the connection
     * @param message message arrived from that connection in bytes
     */
    synchronized void addMessage(String name, List<Byte> message)
    {
        incomingMessages.get(name).addAll(message);
    }

    synchronized InputStream getInputStream(String name)
    {
        byte[] input = new byte[incomingMessages.get(name).size()];
        for(int i=0; i<incomingMessages.get(name).size(); i++)
            input[i] = incomingMessages.get(name).get(i);

        checkReloadSnapshot(name);
        int snapPosition = checkSnapshotMessage(name);
        if(snapshotManager.isSnapshotting())
        {
            if(snapPosition!=-1)
            {
                snapshotManager.messageDuringSnapshot
                        (name, new ArrayList<>(incomingMessages.get(name).subList(0, snapPosition)));
                snapshotManager.snapshotMessageArrived(name);


            }
            else
            {
                snapshotManager.messageDuringSnapshot(name, new ArrayList<>(incomingMessages.get(name)));
            }
        }
        else
        {
            if(snapPosition!=-1)
            {
                snapshotManager.startSnapshot();
                snapshotManager.messageDuringSnapshot(name,
                        new ArrayList<>(incomingMessages.get(name).subList(snapPosition, incomingMessages.get(name).size())));
                snapshotManager.snapshotMessageArrived(name);



            }
        }
        //incomingMessages.get(name).clear();
        return new ByteArrayInputStream(input);
    }

    /**
     * check the presence of the snapshot message in the messages arrived
     * @param name the name of the connection of which we want to check the snapshot message presence
     * @return the position of the snapshot message in the incomingMessages or -1 if the message is not present
     */
    private int checkSnapshotMessage(String name)
    {
        for(int i=0; i <= incomingMessages.get(name).size()-snapshotMessage.length; i++)
        {
            if(incomingMessages.get(name).get(i).equals(snapshotMessage[0]))
            {
                if(incomingMessages.get(name).subList(i, i+snapshotMessage.length).equals(Arrays.asList(snapshotMessage)))
                {
                    incomingMessages.get(name).subList(i, i+snapshotMessage.length).clear();
                    System.out.println("Snapshot message arrived");
                    return i;
                }
            }
        }
        return -1;
    }

    private void checkReloadSnapshot(String name)
    {
        for(int i=0; i <= incomingMessages.get(name).size()-reloadSnapMessage.length; i++)
        {
            if(incomingMessages.get(name).get(i).equals(reloadSnapMessage[0]))
            {
                if(incomingMessages.get(name).subList(i, i+reloadSnapMessage.length).equals(Arrays.asList(reloadSnapMessage)))
                {
                    incomingMessages.get(name).subList(i, i+reloadSnapMessage.length).clear();
                    System.out.println("Reload message arrived");
                    byte[] response = new byte[reloadSnapResp.length];
                    for(int j=0; j<MessageBuffer.reloadSnapResp.length; j++)
                        response[j] = MessageBuffer.reloadSnapResp[j];
                    try {
                        snapshotManager.getOutputStream(name).write(response);
                        System.out.println("Reload response written to " + name);
                        long t = System.currentTimeMillis();
                        while((System.currentTimeMillis() - t) < 7000) Thread.sleep(100);
                    } catch (IOException e)
                    { throw new RuntimeException("Failed to send the response for reloading the snapshot"); } catch (
                            InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        for(String conn: snapshotManager.getConnections()){
                            System.out.println("Closing connection with " + conn);
                            snapshotManager.closeConnection(conn);
                        }
                    } catch (IOException ignored) {}
                    snapshotManager.stopController();
                    try {
                        snapshotManager.closeAccepter();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        SnapshotCreator.snapshotDeserialization(SnapshotCreator.getIdentifier(),snapshotManager.getServerPort(), true);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }

                }
            }
        }
    }
    public List<Byte> getMessages(String name){
        return incomingMessages.get(name);
    }
}