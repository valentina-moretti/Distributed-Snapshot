package org.project;

import com.google.gson.Gson;
import org.application.Controller;

import java.io.*;

public interface ControllerInterface extends Runnable
{
    void run();
    void Serialize();

    public static ControllerInterface Deserialize(int id) throws FileNotFoundException {
        Gson gson = new Gson();
        ControllerInterface controller = null;
        try{
            File objectsFile = new File("Controller"+id+".txt");
            Reader reader = new FileReader(objectsFile);
            Controller inObj = gson.fromJson(reader, Controller.class);
            if(inObj != null)
                controller = inObj;
            else {
                System.out.println("Controller file was corrupted");
                throw new ClassNotFoundException("Controller file was corrupted");
            }
        }catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new FileNotFoundException("File was corrupted");
        }
        return controller;
    }

    void SetSnapshotCreator(SnapshotCreator snapshotCreator);
}
