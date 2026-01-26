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

                if (line.contains("#"))
                {
                    line = line.substring(0, line.indexOf("#")).trim();
                }
            }
        }
    }
    
}

