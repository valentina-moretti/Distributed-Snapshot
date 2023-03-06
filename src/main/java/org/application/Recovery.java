package org.application;

import org.project.SnapshotCreator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

//SBAGLIATO
public class Recovery {
    public void recover(Controller controller){
        Controller object = null;
        for (Serializable s: controller.getSerializableList()) {
            object = (Controller)s;

        }
    }

}
