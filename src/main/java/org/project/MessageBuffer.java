package org.project;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MessageBuffer
{
    private Map<String, List<Byte>> incomingMessages;  //class used as implementation: AbstractQueue

    /*
     *   MessageBuffer(List<String> names)
     *   {
     *       incomingMessages = new HashMap<>();
     *       for (String name : names)
     *           incomingMessages.put(name, new AbstractQueue<Message>());
     *   }
     * // unused constructor
     */
    MessageBuffer()
    {
        incomingMessages = new HashMap<>();
    }

    synchronized void addClient(String name)
    {
        incomingMessages.put(name, new ArrayList<>());
    }

    synchronized void addMessage(String name, List<Byte> message)
    {
        incomingMessages.get(name).addAll(message);
    }

    synchronized InputStream getInputStream(String name)
    {
        byte[] input = new byte[incomingMessages.get(name).size()];
        for(int i=0; i<incomingMessages.get(name).size(); i++)
            input[i] = incomingMessages.get(name).get(i);

        ///////////////////////////////////////////////////

        incomingMessages.get(name).clear();
        return new ByteArrayInputStream(input);
    }

}
