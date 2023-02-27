package org.project;

import java.net.Socket;

/**
 * Thread class used as a listener to incoming messages for a specific socket passed to the constructor
 */
class ConnectionManager extends Thread
{
    private final Socket socket;
    private final MessageBuffer buffer;
    private final String name;

    ConnectionManager(Socket socket, String name, MessageBuffer buffer)
    {
        this.socket = socket;
        this.buffer = buffer;
        this.name = name;
    }
    @Override
    public void run()
    {
        while(!socket.isClosed())
            receive();
    }

    private void receive()
    {
        buffer.addMessage(name, /* ******************************* */);
    }

    public synchronized void send(Message message)
    {

    }
}
