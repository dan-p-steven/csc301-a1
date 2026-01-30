package Shared;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.google.gson.Gson;

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

    public static HttpResponse<String> sendPostRequest(String ip, int port, String endpoint, String body) throws IOException, InterruptedException {

        // create client
        HttpClient client = HttpClient.newHttpClient();

        // build request
        HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://" + ip + ":" + port + endpoint))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

        // return reponse
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp;
    }

    public static HttpResponse<String> sendGetRequest(String ip, int port, String endpoint) throws IOException, InterruptedException {

        // create client
        HttpClient client = HttpClient.newHttpClient();

        // build request
        HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://" + ip + ":" + port + endpoint))
        .header("Content-Type", "application/json")
        .GET()
        .build();

        // return response
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        return resp;
    }
} 
