package org.project;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class MyInputStream extends InputStream
{
    private InputStream inputStream;
    private final String name;
    private final MessageBuffer messageBuffer;

    MyInputStream(MessageBuffer messageBuffer, String connectionName)
    {
        System.out.println(" --- New MyInputStream");
        this.name = connectionName;
        System.out.println(" --- msg");
        this.messageBuffer = messageBuffer;
        System.out.println(" --- msg.getInputStream");
        this.inputStream = messageBuffer.getInputStream(name);
    }

    @Override
    synchronized public int read() throws IOException {
        //this.inputStream = messageBuffer.getInputStream(name);
        if (inputStream.available() == 0) {
            //System.out.println("Original InputStream is not available. \nCreating a new one from messages");
            if (messageBuffer.getMessages(name).size()==0) return -1;
            List<Byte> bytes = messageBuffer.getMessages(name);
            byte[] arrayByte = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                arrayByte[i] = bytes.get(i);
            }
            this.inputStream = new ByteArrayInputStream(arrayByte);
            messageBuffer.getMessages(name).clear();
            if(this.inputStream.available()==0) return -1;
        }
        //System.out.println("Available bytes: " + inputStream.available());
        int byteRead = inputStream.read();
        if (byteRead == -1 && inputStream.available() == 0) {
            // il nuovo stream è terminato
            return -1;
        }
        return byteRead;
    }


    @Override
    public boolean markSupported()
    {
        return false;
    }

    @Override
    public void mark(int readLimit)
    {
        return;
    }

    @Override
    public void reset() throws IOException
    {
        return;
    }
}
