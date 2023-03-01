package org.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread class used as a listener to incoming messages for a specific socket passed to the constructor
 */
class ConnectionManager extends Thread
{
    private final Socket socket;
    private final MessageBuffer buffer;
    private final String name;
    private final SnapshotCreator s;


    ConnectionManager(Socket socket, String name, MessageBuffer buffer, SnapshotCreator s)
    {
        this.socket = socket;
        this.buffer = buffer;
        this.name = name;
        this.s = s;
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
        while (socket.getInputStream().available()!=0)

            readMessage.add((byte) socket.getInputStream().read());

        buffer.addMessage(name, readMessage);

        //TODO: se ho letto lo snapshot o inizio un nuovo snapshot o se è già in corso
        // mi segno che da quello mi è arrivato

        if(0/*leggo snapshot*/){

            synchronized (s.snapshotLock) {
                if (!s.snapshotting) {
                    s.SnapshotStarted();

                } else {
                    //salvo il messaggio
                    s.savedMessages.put(name, readMessage);
                }
            }
        }



    }

    public synchronized OutputStream getOutputStream() throws IOException
    {
        return socket.getOutputStream();
    }

    public synchronized InputStream getInputStream() throws IOException
    {
        return socket.getInputStream();
    }

    public MessageBuffer getBuffer() {
        return buffer;
    }
}
