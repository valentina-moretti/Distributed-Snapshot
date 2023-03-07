package org.project;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;

/**
 * This class is used to convert objects into strings and viceversa using JsonConverter
 */
public class  JsonConverter {
    private static GsonBuilder builder = new GsonBuilder();
    private static Gson gson;

    static{
        builder.setPrettyPrinting();
        builder.registerTypeAdapter(SnapshotCreator.class, new SnapshotCreatorAdapter());
        gson = builder.create();
    }

    public static SnapshotCreator fromJsonToObject(String filename){
        String jsonString;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        String ls = System.getProperty("line.separator");
        while (true) {
            try {
                if (!((line = reader.readLine()) != null)) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            stringBuilder.append(line);
            stringBuilder.append(ls);
        }
        // delete the last new line separator
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SnapshotCreator snapshotCreator = gson.fromJson(line, SnapshotCreator.class);

        return snapshotCreator;
    }

    public static SnapshotCreator fromJsonFileToObject(String filename){
        // Reading the object from a file
        SnapshotCreator snapshotCreator=null;
        try {
            JsonReader reader = new JsonReader(new FileReader(filename));
            snapshotCreator = gson.fromJson(reader, SnapshotCreator.class);
            reader.close();
        }
        catch (IOException ex) {
            System.out.println("IOException is caught");
        }
        return snapshotCreator;
    }



    public static String fromObjectToJson(SnapshotCreator snapshotCreator){
        String jsonString=gson.toJson(snapshotCreator) + "\nEOF\n";
        return(jsonString);
    }
}

class SnapshotCreatorAdapter extends TypeAdapter<SnapshotCreator> {
    private static GsonBuilder builder = new GsonBuilder();
    private static Gson gson;

    static {
        builder.setPrettyPrinting();
        gson = builder.create();
    }

    @Override
    public SnapshotCreator read(JsonReader reader) throws IOException {
        String jsonString= gson.toJson(SnapshotCreator) + "\nEOF\n";
    }

    @Override
    public void write(JsonWriter writer, SnapshotCreator student) throws IOException {
    }
}