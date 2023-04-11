package org.application;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Animal implements Serializable{
    private String name;

    public Animal(String name){
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
