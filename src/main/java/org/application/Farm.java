package org.application;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Farm implements Serializable {
    private List<Animal> animalList;

    public Farm(Controller controller){
        animalList = new ArrayList<>();
    }

    public void addAnimal(Animal animal) {
        this.animalList.add(animal);
    }


    public List<Animal> getAnimalList() {
        return animalList;
    }
}
