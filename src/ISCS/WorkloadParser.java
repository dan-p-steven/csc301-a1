package ISCS;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class WorkloadParser {

    private static final String ORDER_SERVICE_URL = "http://127.0.0.1:14000";

    public static void main(String[] args)
    {
        if (args.length < 1)
        {
            System.out.println("Usage: java WorkloadParser <workload_file_path>");
        }

        String filename = args[0];
        ProcessWorkloadFile(filename);
    }

    private static void processWorkloadFile(String filename)
    {
        try (BufferedReader br = new BufferedReader(new FileReader(filename)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                line = line.trim();

                // skip empty lines and/or comments
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[source]"))
                {
                    continue;
                }

                //handles inline comments
                if (line.contains("#"))
                {
                    line = line.substring(0, line.indexOf("#")).trim();
                }

                try 
                {parseAndSend(line);}
                catch (Exception e)
                {
                    System.out.println("Error processing line: " + line);
                    e.printStackTrace();

                }
            }
        }
    }

    private static void parseAndSend(String line) throws IOException
    {
        String[] parts = line.split ("\\s+");
        if (parts.length < 2) return;

        String type = parts[0].toUpperCase(); //USER, PRODUCT, ORDER
        String command = parts[1].toLowerCase(); //create, get, update, delete

        String endpoint = "";
        String method = "POST";
        String payload = "";

        //finds the endpoint and build JSON based on type
        switch(type)
        {
            case "USER":
                endpoint = "/user";
                if (command.equals("get"))
                {
                    method = "GET";
                }
                else
                {
                    payload = buildUserJson(command, parts);
                }
                break;

            case "PRODUCT":
                endpoint = "/product";
                if (command.equals("info"))
                {
                    method = "GET";
                }
                else
                {
                   payload = buildProductJson(command, parts);
                }
                break;

            case "ORDER":
                endpoint = "/order";
                payload = buildOrderJson(command, parts);
                break;
            
            default:
                System.out.println("Unknown type: " + type);
                return;
        }

        //makes the full url
        String urlString = ORDER_SERVICE_URL + endpoint;

        //if its a get, append the id to the url
        if (method.equals("GET"))
        {
            if (parts.length > 2)
            {
                urlString += "/" + parts[2];
            }
        }

        sendRequest(urlString, method, payload);
    }

    private static void sendRequest(String urlString, String method, String payload) throws IOException
    {
        return;
    }

    private static String buildUserJson(String command, String[] parts)
    {
        // syntax: USER create <id> <username> <email> <password>
        // index:   0     1      2       3        4         5
        return "{}";
    }

    private static String buildProductJson(String command, String[] parts)
    {
        // syntax: PRODUCT add <id> <name> <description> <price> <stock>
        // index:   0       1     2      3          4        5      6
        return "{}";
    }

    private static String buildOrderJson(String command, String[] parts)
    {
        // syntax: ORDER create <product_id> <user_id> <quantity>
        // index:   0      1       2          3          4 
        return "{}";
    }
    
}

