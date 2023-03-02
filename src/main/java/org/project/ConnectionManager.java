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
        List<Byte> newMessage= null;
        while (socket.getInputStream().available()!=0) {
            byte newByte = (byte)socket.getInputStream().read();
            readMessage.add(newByte);
            newMessage.add((Byte) newByte);
        }

        buffer.addMessage(name, readMessage);

        //TODO: se ho letto lo snapshot o inizio un nuovo snapshot o se è già in corso
        // mi segno che da quello mi è arrivato

        //TODO: save state

        if(newMessage.equals(255)/*leggo snapshot*/){

            synchronized (s.snapshotLock) {
                if (!s.snapshotting) {
                    // already called by the one who has started the snapshot
                    s.SnapshotStarted();
                    s.SaveState(name);
                } else {
                    //salvo il messaggio
                    if(!s.getChannelClosed(name).contains(/*socket da cui lo ha ricevuto*/)) {
                        s.savedMessages.put(name, readMessage);
                        s.setChannelClosed(name,/*socket da cui lo ha ricevuto*/ );
                    }
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
