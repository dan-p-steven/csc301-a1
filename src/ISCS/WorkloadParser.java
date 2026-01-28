package ISCS;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        //writing JSON body
        if (!method.equals("GET") && !payload.isEmpty())
        {
            try (OutputStream os = conn.getOutputStream())
            {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        //get response
        int status = conn.getResponseCode();
        System.out.printf("Sent: %s %-40s | Payload: %-50s | Status: %d%n", 
                          method, urlString, 
                          (payload.length() > 50 ? payload.substring(0, 47) + "..." : payload), 
                          status);

        conn.disconnect();
    }

    //helper funcs
    
    private static String buildUserJson(String command, String[] parts)
    {
        // syntax: USER create <id> <username> <email> <password>
        // index:   0     1      2       3        4         5
        
        String id = (parts.length > 2) ? parts[2] : "";
        
        if (command.equals("update")) {
            // Handle specialized update syntax: username:name email:foo@bar ...
            String username = extractValue(parts, "username:");
            String email = extractValue(parts, "email:");
            String password = extractValue(parts, "password:");
            
            return String.format("{\"command\":\"%s\", \"id\":%s, \"username\":\"%s\", \"email\":\"%s\", \"password\":\"%s\"}",
                    command, id, username, email, password);
        }

        // Standard create/delete
        String username = (parts.length > 3) ? parts[3] : "";
        String email = (parts.length > 4) ? parts[4] : "";
        String password = (parts.length > 5) ? parts[5] : "";

        return String.format("{\"command\":\"%s\", \"id\":%s, \"username\":\"%s\", \"email\":\"%s\", \"password\":\"%s\"}",
                command, id, username, email, password);
    }

    private static String buildProductJson(String command, String[] parts)
    {
        // syntax: PRODUCT add <id> <name> <description> <price> <stock>
        // index:   0       1     2      3          4        5      6
        String id = (parts.length > 2) ? parts[2] : "";

        if (command.equals("update")) {
            // PRODUCT update 4 name:granola price:4.99 quantity:20
            String name = extractValue(parts, "name:");
            String description = extractValue(parts, "description:");
            String price = extractValue(parts, "price:");
            String quantity = extractValue(parts, "quantity:");
            
            // Default numeric fields to 0 if missing to avoid JSON errors
            if (price.isEmpty()) price = "0";
            if (quantity.isEmpty()) quantity = "0";

            return String.format("{\"command\":\"%s\", \"id\":%s, \"name\":\"%s\", \"description\":\"%s\", \"price\":%s, \"quantity\":%s}",
                    command, id, name, description, price, quantity);
        }

        // Standard create/delete
        String name = (parts.length > 3) ? parts[3] : "";
        String description = (parts.length > 4) ? parts[4] : "";
        String price = (parts.length > 5) ? parts[5] : "0";
        String quantity = (parts.length > 6) ? parts[6] : "0";

        return String.format("{\"command\":\"%s\", \"id\":%s, \"name\":\"%s\", \"description\":\"%s\", \"price\":%s, \"quantity\":%s}",
                command, id, name, description, price, quantity);
    }

    private static String buildOrderJson(String command, String[] parts)
    {
        // syntax: ORDER create <product_id> <user_id> <quantity>
        // index:   0      1       2          3          4 
        String productId = (parts.length > 2) ? parts[2] : "";
        String userId = (parts.length > 3) ? parts[3] : "";
        String quantity = (parts.length > 4) ? parts[4] : "";

        return String.format("{\"command\":\"place order\", \"product_id\":%s, \"user_id\":%s, \"quantity\":%s}",
                productId, userId, quantity);
    }

    private static String extractValue(String[] parts, String prefix) {
        for (String part : parts) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return "";
    }
    
}

