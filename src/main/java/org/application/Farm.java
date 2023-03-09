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

    public Animal getAnimal(String name){
        for (int i=0; i< animalList.size(); i++) {
            if(Objects.equals(animalList.get(i).getName(), name)){
                return animalList.remove(i);
            }
        }
        return null;
    }

}
