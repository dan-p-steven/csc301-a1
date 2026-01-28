package ISCS;

import Shared.MicroService;
import Shared.ServerConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.security.ntlm.Server;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.reflect.Type;
import java.util.Map;


public class ISCS extends MicroService 
{
    private static final String SERVICE_NAME = "InterServiceCommuncation";
    private static final Gson gson = new Gson();

    private final ServerConfig userServiceConfig;
    private final ServerConfig productServiceConfig;

    public ISCS(String ip, int port, ServerConfig userConfig, ServerConfig productConfig) throws IOException 
    {
        super(ip, port);
        this.userServiceConfig = userConfig;
        this.productServiceConfig = productConfig;

        // Route /user requests to the UserService
        addContext("/user", new ForwardHandler(userServiceConfig));
        
        // Route /product requests to the ProductService
        addContext("/product", new ForwardHandler(productServiceConfig));
    }

    static class ForwardHandler implements HttpHandler
    {
        private final ServerConfig targetConfig;

        public ForwardHandler(ServerConfig targetConfig) 
        {
            this.targetConfig = targetConfig;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException
        {
            //build target URL
            String path = exchange.getRequestURI().toString();
            String targetUrlString = "http://" + targetConfig.ip + ":" + targetConfig.port + path;

            System.out.println("[ISCS] Forwarding request to: " + exchange.getRequestMethod() + targetUrlString);

            //open connection to target service
            URL targetUrl = new URL(targetUrlString);
            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
            conn.setRequestMethod(exchange.getRequestMethod());
            conn.setDoOutput(true);

            // forward headers
            if (exchange.getRequestBody().available() > 0 || "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody();
                     OutputStream os = conn.getOutputStream()) {
                    is.transferTo(os); 
                }
            }

            //get response code
            int responseCode;
            try
            {
                responseCode = conn.getResponseCode();
            }
            catch (IOException e)
            {
                responseCode = 503;
            }

            //read response
            InputStream responseStream = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
            byte[] responseBytes = new byte[0];

            if (responseStream != null) 
            {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                responseStream.transferTo(buffer);
                responseBytes = buffer.toByteArray();
            }

            //send response back to original client
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) 
            {
                os.write(responseBytes);
            }
        }
    }

    public static void main(String[] args) throws IOException
    {
        if (args.length < 1)
        {
            System.out.println("Usage: java ISCS <config_file_path>");
            return;
        }

        String configFilePath = args[0];

        // Load server configurations
        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map<String, ServerConfig> servers = gson.fromJson(new FileReader(configFilePath), type);  
        ServerConfig myConfig = servers.get(SERVER_NAME);
        ServerConfig userConfig = servers.get("UserService");
        ServerConfig productConfig = servers.get("ProductService");

        if (myConfig == null || userConfig == null || productConfig == null)
        {
            System.err.println("Missing server configuration.");
            return;
        }

        // Start ISCS server
        ISCS iscs = new ISCS(myConfig.ip, myConfig.port, userConfig, productConfig);
        iscs.start();
    }

}

