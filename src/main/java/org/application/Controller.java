package org.application;

import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Controller implements Runnable{
    //todo: valentina chiede: ci sta il singleton?
    private static Controller instance;
    public static Controller getInstance(){
        if (instance == null){
            instance = new Controller();
        }
        return instance;
    }
    private Controller(){
        instance=null;
    }
    @Override
    public void run(){
        Farm farm = new Farm(this);
        Animal a = new Animal(this);
        farm.addAnimal(a, this);
        Animal b = new Animal(this);
        farm.addAnimal(b, this);
        a.addFriend(b);
        b.addEnemy(a);
    }





}
