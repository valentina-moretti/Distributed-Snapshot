package org.project;

import java.net.Socket;

/**
 * Thread class used as a listener to incoming messages for a specific socket passed to the constructor
 */
class ConnectionManager extends Thread
{
    private final Socket socket;

    ConnectionManager(Socket socket)
    {
        this.socket = socket;
    }
    @Override
    public void run()
    {
        while(!socket.isClosed())
            receive();
    }

    private void receive()
    {

    }

    public synchronized void send(Message message)
    {

    }
}
