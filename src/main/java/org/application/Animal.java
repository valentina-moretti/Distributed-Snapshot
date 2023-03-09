package org.application;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Animal implements Serializable{
    private List<Animal> friendsList;
    private List<Animal> enemiesList;
    private String name;

    public Animal(String name){
        this.name = name;
        friendsList = new ArrayList<>();
        enemiesList = new ArrayList<>();
    }

    public void addEnemy(Animal enemyList) {
        this.enemiesList.add(enemyList);
    }

    public void addFriend(Animal enemyList) {
        this.friendsList.add(enemyList);
    }

    public String getName() {
        return name;
    }
}
