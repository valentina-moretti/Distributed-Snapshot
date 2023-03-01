package org.project;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MyOutputStream extends OutputStream {

    private final SnapshotCreator s;
    private final OutputStream outputStream;

    MyOutputStream(SnapshotCreator s, OutputStream outputStream){
        this.s=s;
        this.outputStream = outputStream;
    }

    @Override
    public void write(int b) throws IOException {
        synchronized (s.snapshotLock){
            while (s.snapshotting){
                try {
                    s.snapshotLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            outputStream.write(b);
        }
    }
}
