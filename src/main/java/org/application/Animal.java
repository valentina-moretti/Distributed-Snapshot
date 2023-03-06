package org.application;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Animal {
    private List<Animal> friendsList;
    private List<Animal> enemiesList;

    public Animal(Controller controller){
        friendsList = new ArrayList<>();
        enemiesList = new ArrayList<>();
        controller.addSerializable((Serializable) this);
    }

    public void addEnemy(Animal enemyList) {
        this.enemiesList.add(enemyList);
    }

    public void addFriend(Animal enemyList) {
        this.friendsList.add(enemyList);
    }



}
