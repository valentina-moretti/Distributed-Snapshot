package org.project;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread class used as a listener to incoming messages for a specific socket passed to the constructor
 */
class ConnectionManager extends Thread
{
    private transient final Socket socket;
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
            try {
                receive();
            } catch (IOException e) { try{ socket.close(); } catch (IOException ignored){} }
    }

    private void receive() throws IOException
    {
        List<Byte> readMessage = new ArrayList<>();
        while (socket.getInputStream().available()!=0) {
            readMessage.add((byte) socket.getInputStream().read());
            if (!socket.getInetAddress().isReachable(5000)) socket.close();
            if (socket.isClosed()) return;
        }
        if(readMessage.size()>0)
        {
            System.out.println("Message from " + name + ":");
            System.out.println(readMessage);
            buffer.addMessage(name, readMessage);
            //System.out.println("Available before getInputStream: " + (socket.getInputStream().available()!=0));
            while(buffer.isPausedReceiver()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            buffer.getInputStream(name);
            //System.out.println("Available after getInputStream: " + (socket.getInputStream().available()!=0));
        }

    }

    synchronized OutputStream getOutputStream() throws IOException
    {
        return socket.getOutputStream();
    }

    public String getIp() {
        return name;
    }

    public void close() throws IOException
    {
        socket.close();
    }

    Socket getSocket() {
        return socket;
    }
}
