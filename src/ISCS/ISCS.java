package ISCS;

import Shared.MicroService;
import Shared.ServerConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
    pricvate final ServerConfig productServiceConfig;

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
            String path = exchange.getRequestURI().toString();
            String targetUrlString = "http://" + targetConfig.ip + ":" + targetConfig.port + path;

            System.out.println("[ISCS] Forwarding request to: " + exchange.getRequestMethod() + targetUrlString);
        }
    }

}

