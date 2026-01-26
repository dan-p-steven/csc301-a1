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
    
}

