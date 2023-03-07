package org.application;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Farm {
    private List<Animal> animalList;

    public Farm(Controller controller){

        animalList = new ArrayList<>();
    }

    public void addAnimal(Animal animal, Controller controller) {
        this.animalList.add(animal);
    }

}
