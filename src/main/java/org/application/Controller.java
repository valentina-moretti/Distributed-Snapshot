package org.application;

import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Controller implements Runnable{
    private List<Socket> connectionsList;
    private List<Serializable> serializableList;

    public Controller(){
        connectionsList= new ArrayList<>();
        serializableList= new ArrayList<>();
    }

    public void start_application(){
        Farm farm = new Farm(this);
        Animal a = new Animal(this);
        farm.addAnimal(a, this);
        Animal b = new Animal(this);
        farm.addAnimal(b, this);
        a.addFriend(b);
        b.addEnemy(a);
    }

    public void addSerializable(Serializable serializable){
        serializableList.add(serializable);
    }

    public List<Serializable> getSerializableList() {
        return serializableList;
    }

    @Override
    public void run() {

    }
}
