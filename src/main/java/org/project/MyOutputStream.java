package org.project;

import java.io.IOException;
import java.io.OutputStream;

class MyOutputStream extends OutputStream
{
    private final SnapshotCreator snapshotManager;
    private final OutputStream outputStream;

    MyOutputStream(SnapshotCreator s, OutputStream outputStream)
    {
        this.snapshotManager=s;
        this.outputStream = outputStream;
    }

    @Override
    synchronized public void write(int b) throws IOException
    {
        while (snapshotManager.isSnapshotting())
        {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        outputStream.write(b);
    }
}
