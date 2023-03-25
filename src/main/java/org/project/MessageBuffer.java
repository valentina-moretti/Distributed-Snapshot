package org.project;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

class MessageBuffer
{
    static final Byte[] snapshotMessage =
            {(byte)255, (byte)255, (byte)255, (byte)255, (byte)112, (byte)255, (byte)255, (byte)255, (byte)255,
             (byte)255, (byte)255, (byte)255, (byte)255, (byte)112, (byte)255, (byte)255, (byte)255, (byte)255};
    private final Map<String, List<Byte>> incomingMessages;
    private final SnapshotCreator snapshotManager;

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
        System.out.println("getInputStream");
        byte[] input = new byte[incomingMessages.get(name).size()];
        for(int i=0; i<incomingMessages.get(name).size(); i++)
            input[i] = incomingMessages.get(name).get(i);

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
        incomingMessages.get(name).clear();
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
                    System.out.println("Snapshot message arrived");
                    return i;
                }
            }
        }
        return -1;
    }
}