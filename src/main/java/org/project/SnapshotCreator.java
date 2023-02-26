package org.project;

import java.io.Serializable;
import java.net.Socket;
import java.util.List;
import java.util.Map;


public abstract class SnapshotCreator
{
    private List<Serializable> savedObjects;
    private Map<Socket, List<Message>> incomingMessages; // modificare la lista in queue
    private Map<String, Socket> sockets;
    private List<Socket> connections;

    public String connect_to()
    {
        Socket socket;
        Listener listener = new Listener(this, socket);

    }

    synchronized void addMessage(Socket)
    {

    }

    synchronized public Message receive(String name)
    {
        //popMessage
    }

    public void send(String name, Message )
    {

    }

    public void add_entity(Serializable newObject)
    {

    }
}
