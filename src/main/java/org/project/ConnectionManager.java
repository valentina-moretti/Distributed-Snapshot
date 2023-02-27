package org.project;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;

/**
 * Thread class used as a listener to incoming messages for a specific socket passed to the constructor
 */
class ConnectionManager extends Thread
{
    private final Socket socket;
    private BufferedReader in;
    private MessageBuffer messageBuffer;

    ConnectionManager(MessageBuffer messageBuffer, Socket socket)
    {
        this.socket = socket;
        this.messageBuffer = messageBuffer;
    }


    @Override
    public void run(){
        while(!socket.isClosed()) {
            this.readFromBuffer();
        }
    }

    private void readFromBuffer() {

        String lastMessage = "";

        try {
            String line = in.readLine();
            while (!("EOF").equals(line)) {
                lastMessage = lastMessage + line + "\n";
                line = in.readLine();
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ex){
                    ex.printStackTrace();
                    System.out.println("SOCKET  ERROR");
                }
                return;
            }
        }

        if(lastMessage==null){
            return;
        }

        Message message = new Message(lastMessage);


    }


    public synchronized void send(Message message)
    {

    }
}
