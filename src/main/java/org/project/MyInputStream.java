package org.project;

import java.io.IOException;
import java.io.InputStream;

class MyInputStream extends InputStream
{
    private InputStream inputStream;
    private final String name;
    private final MessageBuffer messageBuffer;

    MyInputStream(MessageBuffer messageBuffer, String connectionName)
    {
        this.name = connectionName;
        this.messageBuffer = messageBuffer;
        this.inputStream = messageBuffer.getInputStream(name);
    }

    @Override
    synchronized public int read() throws IOException
    {
        if(inputStream.available()==0)
            inputStream = messageBuffer.getInputStream(name);
        return inputStream.read();
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
