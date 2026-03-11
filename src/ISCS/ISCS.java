package ISCS;

import Shared.MicroService;
import Shared.ServerConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
//import com.sun.security.ntlm.Server;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.reflect.Type;
import java.util.Map;


public class ISCS extends MicroService 
{
    private static final String SERVICE_NAME = "InterServiceCommunication";
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
        addContext("/shutdown", exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write("{}".getBytes());
                exchange.getResponseBody().close();
            } catch (Exception e) {}

            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    System.exit(0);
                } catch (Exception e) {}
            }).start();
        });
    }

    static class ForwardHandler implements HttpHandler
    {
        private final ServerConfig targetConfig;

        public ForwardHandler(ServerConfig targetConfig) 
        {
            this.targetConfig = targetConfig;
        }

        @Override
        public void handle(HttpExchange exchange)
        throws IOException
        {
            try {
                // 1. Fire the request asynchronously using our updated HttpUtils
                HttpUtils.forwardRequest(exchange, targetConfig.ip, targetConfig.port)
                    .thenAccept(resp -> {
                        // 2. When the response arrives in the future, forward it back
                        try {
                            HttpUtils.forwardResponse(exchange, resp);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    })
                    .exceptionally(ex -> {
                        // 3. Catch any network or async errors gracefully
                        try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (Exception ignored) {}
                        return null;
                    });

            } catch (IOException e) {
                HttpUtils.sendHttpResponse(exchange, 500, "{}");
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
        ServerConfig myConfig = servers.get(SERVICE_NAME);
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

