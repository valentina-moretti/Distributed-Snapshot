package org.application;

import java.util.ArrayList;
import java.util.List;

public class Farm {
    private List<Animal> animalList;

    public Farm(){
        animalList = new ArrayList<>();
    }

    public void addAnimal(Animal animal) {
        this.animalList.add(animal);
    }

}
