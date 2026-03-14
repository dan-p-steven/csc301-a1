package OrderService;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import Shared.HttpUtils;
import Shared.MicroService;
import Shared.ServerConfig;
import ProductService.Product;
import ProductService.ProductPostRequest;

/**
 * OrderService acting as a Direct Orchestrator.
 * Eliminates ISCS hop and routes directly to User/Product clusters.
 * * @author Daniel Steven
 */
public class OrderService extends MicroService {

    private static final String serverName = "OrderService";
    private static final Gson gson = new Gson();

    // Cluster Information
    private final List<String> userIPs;
    private final int userPort;
    private final List<String> productIPs;
    private final int productPort;

    // Round-Robin Counters
    private final AtomicInteger userCounter = new AtomicInteger(0);
    private final AtomicInteger productCounter = new AtomicInteger(0);

    private final OrderDatabaseManager db;
    private final int DB_POOL = 3; // Reduced pool for 50-machine horizontal scaling

    public OrderService(String ip, int port, 
                        List<String> userIPs, int userPort,
                        List<String> productIPs, int productPort,
                        String jdbcUrl, String dbUser, String dbPassword)
            throws IOException, SQLException, InterruptedException {

        super(ip, port);
        
        this.userIPs = userIPs;
        this.userPort = userPort;
        this.productIPs = productIPs;
        this.productPort = productPort;

        // Register Contexts
        addContext("/order", new OrderHandler());
        addContext("/user/purchased", new OrderHandler());
        addContext("/user", new OrderHandler());
        addContext("/product", new OrderHandler());
        addContext("/shutdown", new OrderHandler());

        this.db = new OrderDatabaseManager(jdbcUrl, dbUser, dbPassword, DB_POOL);
    }

    // Helper methods for Round-Robin IP selection
    private String getNextUserIP() {
        return userIPs.get(Math.abs(userCounter.getAndIncrement() % userIPs.size()));
    }

    private String getNextProductIP() {
        return productIPs.get(Math.abs(productCounter.getAndIncrement() % productIPs.size()));
    }

    class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // 1. Handle Shutdown (Broadcast to clusters)
            if (path.equals("/shutdown")) {
                handleShutdown(exchange);
                return;
            }

            // 2. Direct Proxy Forwarding (Bypassing ISCS)
            if (path.startsWith("/user/purchased/")) {
                _handleUserPurchased(exchange, path);
            } else if (path.startsWith("/user")) {
                HttpUtils.forwardRequest(exchange, getNextUserIP(), userPort)
                    .thenAccept(resp -> { try { HttpUtils.forwardResponse(exchange, resp); } catch (IOException ignored) {} });
            } else if (path.startsWith("/product")) {
                HttpUtils.forwardRequest(exchange, getNextProductIP(), productPort)
                    .thenAccept(resp -> { try { HttpUtils.forwardResponse(exchange, resp); } catch (IOException ignored) {} });
            } else if (path.startsWith("/order")) {
                _handleOrder(exchange);
            } else {
                HttpUtils.sendHttpResponse(exchange, 400, "{}");
            }
        }
    }

    private void _handleOrder(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            HttpUtils.sendHttpResponse(exchange, 400, gson.toJson(new OrderResponse(null, null, null, null, "Invalid Method")));
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8")) {
            OrderRequest req = gson.fromJson(reader, OrderRequest.class);

            // Direct Orchestration: Check User -> Check Product -> Deduct -> Record
            HttpUtils.sendGetRequest(getNextUserIP(), userPort, "/user/" + req.getUserId())
                .thenCompose(userResp -> {
                    if (userResp.statusCode() != 200) {
                        return java.util.concurrent.CompletableFuture.failedFuture(new Exception("User Not Found"));
                    }
                    return HttpUtils.sendGetRequest(getNextProductIP(), productPort, "/product/" + req.getProductId());
                })
                .thenAccept(prodResp -> {
                    try {
                        if (prodResp.statusCode() != 200) {
                            HttpUtils.sendHttpResponse(exchange, 404, gson.toJson(new OrderResponse(null, null, null, null, "Product Not Found")));
                            return;
                        }

                        Product p = gson.fromJson(prodResp.body(), Product.class);
                        if (p.getQuantity() < req.getQuantity()) {
                            HttpUtils.sendHttpResponse(exchange, 400, gson.toJson(new OrderResponse(null, null, null, null, "Exceeded quantity limit")));
                            return;
                        }

                        // Deduct Product Quantity
                        ProductPostRequest update = new ProductPostRequest("update", p.getId(), null, null, null, p.getQuantity() - req.getQuantity());
                        HttpUtils.sendPostRequest(getNextProductIP(), productPort, "/product", gson.toJson(update));

                        // Record in local Order DB
                        db.recordPurchase(req.getUserId(), req.getProductId(), req.getQuantity());

                        HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(new OrderResponse(null, p.getId(), req.getUserId(), req.getQuantity(), "Success")));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .exceptionally(ex -> {
                    try { HttpUtils.sendHttpResponse(exchange, 400, gson.toJson(new OrderResponse(null, null, null, null, "Invalid Request"))); } catch (IOException ignored) {}
                    return null;
                });
        }
    }

    private void _handleUserPurchased(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");
        int userId = Integer.parseInt(parts[3]);

        HttpUtils.sendGetRequest(getNextUserIP(), userPort, "/user/" + userId)
            .thenAccept(userResp -> {
                if (userResp.statusCode() != 200) {
                    try { HttpUtils.sendHttpResponse(exchange, 404, "{}"); } catch (IOException ignored) {}
                    return;
                }
                db.getPurchasesByUser(userId).thenAccept(purchases -> {
                    try { HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(purchases)); } catch (IOException ignored) {}
                });
            });
    }

    private void handleShutdown(HttpExchange exchange) throws IOException {
        // Broadcast shutdown to one node in each cluster (they should handle internal propagation if needed)
        // Or loop through all IPs to be sure.
        userIPs.forEach(ip -> HttpUtils.sendPostRequest(ip, userPort, "/shutdown", "{}"));
        productIPs.forEach(ip -> HttpUtils.sendPostRequest(ip, productPort, "/shutdown", "{}"));
        
        HttpUtils.sendHttpResponse(exchange, 200, "{}");
        new Thread(() -> { try { Thread.sleep(1000); System.exit(0); } catch (Exception ignored) {} }).start();
    }

    public static void main(String[] args) throws Exception {
        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map<String, ServerConfig> servers = gson.fromJson(new FileReader(args[0]), type);

        ServerConfig oCfg = servers.get("OrderService");
        ServerConfig uCfg = servers.get("UserService");
        ServerConfig pCfg = servers.get("ProductService");

        new OrderService(
            oCfg.ips.get(0), oCfg.port,
            uCfg.ips, uCfg.port,
            pCfg.ips, pCfg.port,
            oCfg.db.url, oCfg.db.user, oCfg.db.password
        ).start();
    }
}
