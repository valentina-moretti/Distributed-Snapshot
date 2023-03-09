package org.project;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

class MessageBuffer
{
    private static final Byte[] snapshotMessage =
            {(byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255,
             (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255, (byte)255};
    private final Map<String, List<Byte>> incomingMessages;  //class used as implementation: AbstractQueue
    private final SnapshotCreator snapshotManager;

    MessageBuffer(SnapshotCreator snapshotCreator)
    {
        incomingMessages = new HashMap<>();
        snapshotManager = snapshotCreator;
    }

    synchronized void addClient(String name)
    {
        incomingMessages.put(name, new ArrayList<>());
    }

    synchronized void addMessage(String name, List<Byte> message)
    {
        incomingMessages.get(name).addAll(message);
    }

    synchronized InputStream getInputStream(String name)
    {
        byte[] input = new byte[incomingMessages.get(name).size()];
        for(int i=0; i<incomingMessages.get(name).size(); i++)
            input[i] = incomingMessages.get(name).get(i);

        if(snapshotManager.isSnapshotting())
        {
            if(checkSnapshotMessage(name)!=-1)
            {
                //TODO: salvo i messaggi arrivati fino al messaggio di snapshot e segnalo che da questa
                // connessione è arrivato il messaggio di snapshot
                snapshotManager.snapshotMessageArrived(name);
            }
            else
            {
                //TODO: salva i messaggi arrivati durante lo snapshot
            }
        }
        else
        {
            if(checkSnapshotMessage(name)!=-1)
                snapshotManager.startSnapshot();
            //TODO: salva messagi dopo lo snapshot nei messaggi registrati durante lo snapshot
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
                if(incomingMessages.get(name).subList(i, i+snapshotMessage.length)
                        .equals(Arrays.asList(snapshotMessage)))
                    return i;
            }
        }
        return -1;
    }

    public Map<String, List<Byte>> getIncomingMessages() {
        return incomingMessages;
    }
}