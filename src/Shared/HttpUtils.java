/**
 * Class containing shared http tools used by microservices
 *
 * @author Daniel Steven
 */
package Shared;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.google.gson.Gson;

import java.util.concurrent.CompletableFuture;

public class HttpUtils {

    // One shared client for the lifetime of the application
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();

    /**
     * send back a http response with specific parameters
     *
     * @param httpexchange object for response
     * @param status code
     * @param string body of the response
     */
    public static void sendHttpResponse(HttpExchange exchange, int status, String data) 
    throws IOException{
        // send a specific response back

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

    /**
     * forward a response back to a client
     *
     * @param httpexchange object
     * @param reponse to be forwarded
     */
    public static void forwardResponse(HttpExchange exchange, HttpResponse<String> response) 
    throws IOException {
        // forward a response without any doing anything to it

        // Extract response info
        int statusCode = response.statusCode();
        String responseBody = response.body();

        // Copy Content-Type header if present
        String contentType = response.headers().firstValue("Content-Type").orElse("application/json");
        exchange.getResponseHeaders().set("Content-Type", contentType);

        byte[] responseBytes = responseBody.getBytes("UTF-8");
        // Send response back to original caller
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();

    }

    /**
     * forward a request to another server
     *
     * @param httpexchange object
     * @param destination ip address
     * @pram destination port
     *
     * @return response from server
     *
     */
    public static CompletableFuture<HttpResponse<String>> forwardRequest(HttpExchange exchange, String destIp, int destPort)
    throws IOException {
        // forward request to another machine

        Headers reqHeader = exchange.getRequestHeaders();
        String reqUri = exchange.getRequestURI().toString();
        String reqMethod = exchange.getRequestMethod();

        String destUrl =  "http://" + destIp + ":" + destPort + reqUri;
        String reqBody;

        // Start building the forward request
        HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(destUrl));

        // set the appropriate method
        if (reqMethod.equals("POST")) {

            // populate body and set method to POST
           reqBody = new String(exchange.getRequestBody().readAllBytes());
           builder.POST(HttpRequest.BodyPublishers.ofString(reqBody));

        } else if (reqMethod.equals("PUT")) {

            // populate body and set method to PUT
           reqBody = new String(exchange.getRequestBody().readAllBytes());
           builder.PUT(HttpRequest.BodyPublishers.ofString(reqBody));

        } else if (reqMethod.equals("DELETE")) {
            builder.DELETE();
        } else if (reqMethod.equals("GET")) {
            builder.GET();
        }

         // Copy headers
        String contentType = reqHeader.getFirst("Content-Type");
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }


        // open client and send request
        HttpRequest forwardReq = builder.build();
        return CLIENT.sendAsync(forwardReq, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * send a POST request to a server 
     *
     * @param ip of the server
     * @param port of the server
     * @param endpoint you want to connect to 
     * @param body of the message
     *
     * @return response from the server
     */
    public static CompletableFuture<HttpResponse<String>> sendPostRequest(String ip, int port, String endpoint, String body)
    {

        // build request
        HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://" + ip + ":" + port + endpoint))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

        // return reponse
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * send a GET request to a server 
     *
     * @param ip of the server
     * @param port of the server
     * @param endpoint you want to connect to 
     *
     * @return response from the server
     */

    public static CompletableFuture<HttpResponse<String>> sendGetRequest(String ip, int port, String endpoint)
    {

        // build request
        HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://" + ip + ":" + port + endpoint))
        .header("Content-Type", "application/json")
        .GET()
        .build();

        // return response
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }
} 
