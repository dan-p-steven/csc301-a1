/*
 * A class representing the core business and routing logic of the OrderService.
 *
 * @author Daniel Steven
 */

package OrderService;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.io.IOException;
import java.sql.SQLException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.net.http.HttpResponse;

import OrderService.Order;
import OrderService.OrderRequest;
import OrderService.OrderDatabaseManager;
import ProductService.Product;
import ProductService.ProductPostRequest;
import Shared.MicroService;
import Shared.HttpUtils;
import Shared.ServerConfig;

import java.util.List;
import java.io.FileReader;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;


public class OrderService extends MicroService {

    private static String serverName = "OrderService";

    private static String context        = "/order";
    private static String userContext    = "/user";
    private static String productContext = "/product";

    private static Gson gson = new Gson();

    private String iscsIp;
    private int    iscsPort;

    private int orderCount = 0;
    private int DB_POOL = 10;
    private int THREAD_POOL = 10;

    // Replaces the in-memory userPurchases map + purchases.json file
    private final OrderDatabaseManager db;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public OrderService(String ip, int port, String iscsIp, int iscsPort,
                        String jdbcUrl, String dbUser, String dbPassword)
            throws IOException, SQLException, InterruptedException {

        super(ip, port);
        addContext("/user/purchased", new OrderHandler());
        addContext(context, new OrderHandler());
        addContext(userContext, new OrderHandler());
        addContext(productContext, new OrderHandler());
        addContext("/shutdown", new OrderHandler());
        addContext("/restart", new OrderHandler());

        this.iscsIp  = iscsIp;
        this.iscsPort = iscsPort;
        this.db = new OrderDatabaseManager(jdbcUrl, dbUser, dbPassword, DB_POOL);
    }

    // ------------------------------------------------------------------
    // HTTP routing
    // ------------------------------------------------------------------

    class OrderHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String path = exchange.getRequestURI().getPath();

            if (path.equals("/shutdown")) {
                try {
                    HttpUtils.sendPostRequest(iscsIp, iscsPort, "/user/shutdown", "{}");
                    HttpUtils.sendPostRequest(iscsIp, iscsPort, "/product/shutdown", "{}");
                    HttpUtils.sendPostRequest(iscsIp, iscsPort, "/shutdown", "{}");
                } catch (Exception e) {
                    System.out.println("failed to shutdown");
                }

                HttpUtils.sendHttpResponse(exchange, 200, "{}");

                new Thread(() -> {
                    try { Thread.sleep(1000); System.exit(0); }
                    catch (Exception e) {}
                }).start();
                return;
            }

            if (path.equals("/restart")) {
                HttpUtils.sendHttpResponse(exchange, 200, "{}"); return;
            }

            // Must come BEFORE the generic /user forward
            if (path.startsWith("/user/purchased/")) {
                _handleUserPurchased(exchange, path);
                return;
            }

            // Async Proxy Forwarding
            if (path.startsWith("/user") || path.startsWith("/product")) {
                try {
                    HttpUtils.forwardRequest(exchange, iscsIp, iscsPort)
                        .thenAccept(resp -> {
                            try {
                                HttpUtils.forwardResponse(exchange, resp);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        })
                        .exceptionally(ex -> {
                            try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (Exception ignored) {}
                            return null;
                        });
                } catch (IOException e) {
                    HttpUtils.sendHttpResponse(exchange, 500, "{}");
                }

            } else if (path.startsWith("/order")) {
                _handleOrder(exchange);

            } else {
                HttpUtils.sendHttpResponse(exchange, 400, "{}");
            }
        }
    }

    private void _handleOrder(HttpExchange exchange) 
    throws IOException {

        try {
            String        method = exchange.getRequestMethod();

            if (!method.equals("POST")) {
                OrderResponse ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                HttpUtils.sendHttpResponse(exchange, 400, gson.toJson(ordResp)); return;
            }

            InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
            OrderRequest req = gson.fromJson(reader, OrderRequest.class);

            if (!req.getCommand().equals("place order") || req.getQuantity() <= 0) {
                OrderResponse ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                HttpUtils.sendHttpResponse(exchange, 400, gson.toJson(ordResp)); return;
            }

            // 1. Fetch user asynchronously
            HttpUtils.sendGetRequest(iscsIp, iscsPort, "/user/" + req.getUserId())
                .thenCompose(userResp -> {
                    if (userResp.statusCode() != 200) {
                        try {
                            OrderResponse err = new OrderResponse(null, null, null, null, "Invalid Request");
                            HttpUtils.sendHttpResponse(exchange, userResp.statusCode(), gson.toJson(err));
                        } catch (IOException ignored) {}
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }

                    // 2. Fetch product asynchronously
                    return HttpUtils.sendGetRequest(iscsIp, iscsPort, "/product/" + req.getProductId())
                        .thenAccept(prodResp -> {
                            try {
                                if (prodResp.statusCode() != 200) {
                                    OrderResponse err = new OrderResponse(null, null, null, null, "Invalid Request");
                                    HttpUtils.sendHttpResponse(exchange, prodResp.statusCode(), gson.toJson(err));
                                    return;
                                }

                                // 3. Check product quantity
                                Product p = gson.fromJson(prodResp.body(), Product.class);
                                if (p.getQuantity() <= req.getQuantity()) {
                                    OrderResponse err = new OrderResponse(null, null, null, null, "Exceeded quantity limit");
                                    HttpUtils.sendHttpResponse(exchange, 400, gson.toJson(err));
                                    return;
                                }

                                // 4. Deduct quantity (Fire and forget async POST)
                                ProductPostRequest prodUpdateReq = new ProductPostRequest(
                                    "update", p.getId(), null, null, null, p.getQuantity() - req.getQuantity()
                                );
                                HttpUtils.sendPostRequest(iscsIp, iscsPort, "/product", gson.toJson(prodUpdateReq));

                                // 5. Record purchase in DB
                                db.recordPurchase(req.getUserId(), req.getProductId(), req.getQuantity());

                                // 6. Send final success response
                                OrderResponse successResp = new OrderResponse(null, p.getId(), req.getUserId(), req.getQuantity(), "Success");
                                HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(successResp));

                            } catch (Exception e) {
                                e.printStackTrace();
                                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                            }
                        });
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                    return null;
                });

        } catch (JsonSyntaxException e) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}");
        }
    }

     // ------------------------------------------------------------------
    // Purchase history handler
    // ------------------------------------------------------------------

    /**
     * GET /user/purchased/{userId}
     * Returns a map of productId -> totalQuantity for the given user.
     * e.g. { "4": 23, "5": 11, "2": 1 }
     */

    private void _handleUserPurchased(HttpExchange exchange, String path) 
    throws IOException {
        String[] parts = path.split("/");
        if (parts.length != 4) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        int userId;
        try {
            userId = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        // Verify user exists asynchronously, then fetch purchases
        HttpUtils.sendGetRequest(iscsIp, iscsPort, "/user/" + userId)
            .thenAccept(userResp -> {
                try {
                    if (userResp.statusCode() != 200) {
                        HttpUtils.sendHttpResponse(exchange, 404, "{}");
                        return;
                    }

                    // FIX: Handle the CompletableFuture returned by the database!
                    db.getPurchasesByUser(userId)
                        .thenAccept(purchases -> {
                            try {
                                HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(purchases));
                            } catch (IOException ignored) {}
                        })
                        .exceptionally(dbEx -> {
                            dbEx.printStackTrace();
                            try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                            return null;
                        });

                } catch (Exception e) {
                    e.printStackTrace();
                    try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                }
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                return null;
            });
    }
  
    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static void main(String[] args) throws IOException, SQLException, InterruptedException {

        String configPath = args[0];

        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map<String, ServerConfig> servers =
            gson.fromJson(new FileReader(configPath), type);

        ServerConfig config     = servers.get(serverName);
        ServerConfig iscsConfig = servers.get("InterServiceCommunication");

        OrderService service = new OrderService(
            config.ip,
            config.port,
            iscsConfig.ip,
            iscsConfig.port,
            config.db.url,
            config.db.user,
            config.db.password
        );

        service.start();
    }
}
