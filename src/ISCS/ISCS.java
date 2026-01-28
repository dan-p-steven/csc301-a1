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


public class ISCS extends MicroService {

    private static final String SERVICE_NAME = "InterServiceCommuncation";
    private static final Gson gson = new Gson();

    private final ServerConfig userServiceConfig;
    pricvate final ServerConfig productServiceConfig;

    
    
}
