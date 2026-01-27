package Shared;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HttpUtils {

    public static void sendHttpResponse(HttpExchange exchange, int status, String data) throws IOException{

        // set the headers to json (important)
        exchange.getResponseHeaders().set("Content-Type", "application/json");

        // get bytes of data
        byte[] dataBytes = data.getBytes("UTF-8");

        // send response headers (status and length of bytes in response body)
        exchange.sendResponseHeaders(status, dataBytes.length);

        // send response body
        OutputStream responseBodyStream = exchange.getResponseBody();
        responseBodyStream.write(dataBytes);
        responseBodyStream.close();

    }
} 
