package org.application;


import org.project.SnapshotCreator;

import java.io.IOException;
import java.io.Serializable;

public class Main {

    public static void main(String[] args){
        Farm farm = new Farm();
        Animal a = new Animal();
        SnapshotCreator snapshotCreator = null;
        try {
            snapshotCreator = new SnapshotCreator((Serializable) a);
        } catch (IOException e) {
            e.printStackTrace();
        }
        farm.addAnimal(a);
        Animal b = new Animal();
        farm.addAnimal(b);
        a.addFriend(b);
        b.addEnemy(a);


    }


}
