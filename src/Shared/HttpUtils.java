package Shared;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public class HttpUtils {

    // 1. Dedicated thread pool for high-concurrency async I/O
    // Using a fixed pool sized to handle hundreds of concurrent network flights
    private static final ExecutorService HTTP_EXECUTOR = Executors.newFixedThreadPool(200);

    // 2. Shared high-performance client
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .executor(HTTP_EXECUTOR) // Use our custom pool
        .connectTimeout(Duration.ofMillis(500)) // Don't wait forever for a dead node
        .build();

    public static void sendHttpResponse(HttpExchange exchange, int status, String data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] dataBytes = data.getBytes("UTF-8");
        exchange.sendResponseHeaders(status, dataBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(dataBytes);
        }
    }

    public static void forwardResponse(HttpExchange exchange, HttpResponse<String> response) throws IOException {
        int statusCode = response.statusCode();
        String responseBody = response.body();
        String contentType = response.headers().firstValue("Content-Type").orElse("application/json");
        
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] responseBytes = responseBody.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    public static CompletableFuture<HttpResponse<String>> forwardRequest(HttpExchange exchange, String destIp, int destPort) throws IOException {
        String destUrl = "http://" + destIp + ":" + destPort + exchange.getRequestURI().toString();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(destUrl))
            .timeout(Duration.ofMillis(1000)); // Total request timeout

        String method = exchange.getRequestMethod();
        if (method.equals("POST") || method.equals("PUT")) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null) builder.header("Content-Type", contentType);

        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public static CompletableFuture<HttpResponse<String>> sendPostRequest(String ip, int port, String endpoint, String body) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://" + ip + ":" + port + endpoint))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(1000))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }

    public static CompletableFuture<HttpResponse<String>> sendGetRequest(String ip, int port, String endpoint) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://" + ip + ":" + port + endpoint))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(1000))
            .GET()
            .build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }
}
