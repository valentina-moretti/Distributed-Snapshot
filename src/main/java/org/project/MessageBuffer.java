package org.project;

import java.util.*;

class MessageBuffer
{
    private Map<String, Queue<Message>> incomingMessages;  //class used as implementation: AbstractQueue

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
        incomingMessages.put(name, new AbstractQueue<Message>());
    }

    synchronized void addMessage(String name, Message message)
    {
        incomingMessages.get(name).add(message);
    }

    synchronized Message popMessage(String name)
    {
        return incomingMessages.get(name).remove();
    }

    synchronized  Message peekMessage(String name)
    {
        return incomingMessages.get(name).peek();
    }
}
