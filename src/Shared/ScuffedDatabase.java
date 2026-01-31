package Shared;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import java.io.FileWriter;
import java.io.FileReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class ScuffedDatabase {

    private static Gson gson = new Gson();

    public static <T> void writeToFile(ArrayList<T> list, String filename) throws IOException {

        String json = gson.toJson(list);

        FileWriter writer = new FileWriter(filename);
        writer.write(json);
        writer.close();
    }

    public static <T> ArrayList<T> readFromFile(String filename, Type listType) throws IOException {

        try {

            FileReader reader = new FileReader(filename);
            ArrayList<T> list = gson.fromJson(reader, listType);

            reader.close();
            return list;

            } catch (FileNotFoundException e) {
                System.out.println("File not found exception, created new list");
                return new ArrayList<T>();
        }
    }
}
