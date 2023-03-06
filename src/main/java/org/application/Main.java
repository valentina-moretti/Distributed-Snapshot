package org.application;


import org.project.SnapshotCreator;

import java.io.IOException;
import java.io.Serializable;

public class Main {

    public static void main(String[] args){
        Controller controller= new Controller();
        try {
            SnapshotCreator snapshotCreator = new SnapshotCreator((Serializable) controller);
        } catch (IOException e) {
            e.printStackTrace();
        }
        controller.start_application();
        Recovery recovery= new Recovery();
        recovery.recover(controller);
    }


}
