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

public class HttpUtils {

    /**
     * send back a http response with specific parameters
     *
     * @param httpexchange object for response
     * @param status code
     * @param string body of the response
     */
    public static void sendHttpResponse(HttpExchange exchange, int status, String data) throws IOException{
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
    public static void forwardResponse(HttpExchange exchange, HttpResponse<String> response) throws IOException {
        // forward a response without any doing anything to it

        // Extract response info
        int statusCode = response.statusCode();
        String responseBody = response.body();

        // Copy Content-Type header if present
        String contentType = response.headers().firstValue("Content-Type").orElse("application/json");
        exchange.getResponseHeaders().set("Content-Type", contentType);

        // Send response back to original caller
        exchange.sendResponseHeaders(statusCode, responseBody.length());
        OutputStream os = exchange.getResponseBody();
        os.write(responseBody.getBytes());
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
    public static HttpResponse<String> forwardRequest(HttpExchange exchange, String destIp, int destPort) throws IOException, InterruptedException {
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
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(forwardReq, HttpResponse.BodyHandlers.ofString());

        // return the response
        return resp;
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

    /**
     * send a GET request to a server 
     *
     * @param ip of the server
     * @param port of the server
     * @param endpoint you want to connect to 
     *
     * @return response from the server
     */

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
