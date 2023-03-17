package org.project;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Thread class used to handle incoming connections
 */
class ConnectionAccepter extends Thread
{
    private final ServerSocket serverSocket;
    private final SnapshotCreator snapC;

    /**
     * @param snap parameter used in order to add a new socket (a connection accepted) to che SnapshotCreator
     * @throws IOException
     */
    ConnectionAccepter(SnapshotCreator snap) throws IOException
    {
        serverSocket = new ServerSocket(snap.getServerPort());
        snapC = snap;
    }

    @Override
    public void run()
    {
        Socket connection;
        while (true)
        {
            try {
                connection = serverSocket.accept();
                snapC.connectionAccepted(connection);
            } catch (IOException ignored) {}
        }
    }
}
