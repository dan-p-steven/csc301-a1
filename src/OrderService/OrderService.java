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
import java.io.ObjectInputStream.GetField;
import java.security.Security;
import java.util.ArrayList;
import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.net.http.HttpResponse;

import OrderService.Order;
import OrderService.OrderRequest;
import ProductService.Product;
import ProductService.ProductPostRequest;
import Shared.MicroService;
import Shared.SecurityUtils;
import Shared.HttpUtils;
import Shared.ServerConfig;

import java.util.ArrayList;
import java.util.List;

// These are to load server config
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.Map;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;

public class OrderService extends MicroService {

    /** Name of the service */
    private static String serverName = "OrderService";
    private static String purchasesDbPath = "data/purchases.json";

    /** Available contexts for the service */
    private static String context = "/order";
    private static String userContext = "/user";
    private static String productContext = "/product";

    /** Database (not used) */
    private List<Order> orders = new ArrayList<>();

    /** List of user purchases */
    // Tracks purchases per user: userId -> { productId -> totalQuantity }
    private Map<Integer, Map<Integer, Integer>> userPurchases = new HashMap<>();

    /** JSON deserializer */
    private static Gson gson = new Gson();

    /** IP and port information about ISCS */
    private String iscsIp;
    private int iscsPort;

    /** Variable to track the Order ID */
    private int orderCount = 0;

    private boolean isFirstCommand = true;

    /** Constructor for the OrderService class.
     *
     * @param IP of the service.
     * @param Port of the service.
     * @param IP of the ISCS
     * @param Port of the ISCS
     */
    public OrderService (String ip, int port,
                        String iscsIp, int iscsPort) throws IOException {

        super(ip, port);
        addContext("/user/purchased", new OrderHandler()); 
        addContext(context, new OrderHandler());
        addContext(userContext, new OrderHandler());
        addContext(productContext, new OrderHandler());
        addContext("/shutdown", new OrderHandler()); 
        addContext("/restart", new OrderHandler());

        this.iscsIp = iscsIp;
        this.iscsPort = iscsPort;

        try (FileReader reader = new FileReader(purchasesDbPath)) {
            Type t = new TypeToken<Map<Integer, Map<Integer, Integer>>>(){}.getType();
            Map<Integer, Map<Integer, Integer>> loaded = gson.fromJson(reader, t);
            this.userPurchases = (loaded != null) ? loaded : new HashMap<>();
        } catch (FileNotFoundException e) {
            this.userPurchases = new HashMap<>();
        }

    }


    /** Custom HttpHandler to implement business logic */
    class OrderHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String path = exchange.getRequestURI().getPath();
            HttpResponse<String> resp;

            if (isFirstCommand)
            {
                isFirstCommand = false;
                if (path.equals("/restart"))
                {
                    HttpUtils.sendHttpResponse(exchange, 200, "{}"); return;
                }
                else
                {
                    try
                    {
                        // wipe out purchase db
                        userPurchases = new HashMap<>();
                        FileWriter writer = new FileWriter(purchasesDbPath);
                        gson.toJson(userPurchases, writer);
    
                        // wipe out user and product dbs
                        HttpUtils.sendPostRequest(iscsIp, iscsPort, "/user/wipe", "{}");
                        HttpUtils.sendPostRequest(iscsIp, iscsPort, "/product/wipe", "{}");
                    }
                    catch (Exception e)
                    {
                        System.out.println("failed to send wipe commands");
                    }
                }
            }

            if (path.equals("/shutdown"))
            {
                try
                {//shut down backend service/iscs
                    HttpUtils.sendPostRequest(iscsIp, iscsPort, "/user/shutdown", "{}");
                    HttpUtils.sendPostRequest(iscsIp, iscsPort, "/product/shutdown", "{}");
                    HttpUtils.sendPostRequest(iscsIp, iscsPort, "/shutdown", "{}"); 
                }
                catch (Exception e)
                {
                    System.out.println("failed to shutdown");
                }

                HttpUtils.sendHttpResponse(exchange, 200, "{}");

                //kill this service
                new Thread(() -> {
                    try 
                    {
                        Thread.sleep(1000);
                        System.exit(0);
                    }
                    catch (Exception e)
                    {}
                }).start();
                return;
            }

            if (path.equals("/restart")) {
                HttpUtils.sendHttpResponse(exchange, 200, "{}"); return;
            }

            // Handle purchased history — must come BEFORE the generic /user forward
            if (path.startsWith("/user/purchased/")) {
                _handleUserPurchased(exchange, path);
                return;
            }

            if (path.startsWith("/user") || path.startsWith("/product")) {
                // forward to icsc
                try {
                    resp = HttpUtils.forwardRequest(exchange, iscsIp, iscsPort);
                    // forward response 
                    HttpUtils.forwardResponse(exchange, resp);

                } catch (InterruptedException e) {
                    HttpUtils.sendHttpResponse(exchange, 500, "{}"); return;
                } catch (IOException e) {
                    HttpUtils.sendHttpResponse(exchange, 500, "{}"); return;
                }

            } else if (path.startsWith("/order")) {
                // handle the /order endpoint here using a helper function
                _handleOrder(exchange);

            } else {
                // return error
                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }
        }
    }

    /** Helper function for handling the OrderService routing and business logic.
     *
     * @param HttpExchange variable containing used to send back responses.
     */
    private void _handleOrder(HttpExchange exchange) throws IOException {
        
        try {
	System.out.println("Recieved req");
        String errBody;
        OrderResponse ordResp;
        String method = exchange.getRequestMethod();

        if (method.equals("POST")) {


            // convert to request object
            InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");

            OrderRequest req = gson.fromJson(reader, OrderRequest.class);

            // extract command
            if (req.getCommand().equals("place order")) {
                // verify if user id exists
                //try
                try {
                    // can not order less than 0 quantity
                    //
                    if (req.getQuantity() <= 0) {

                        ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                        errBody = gson.toJson(ordResp);
                        HttpUtils.sendHttpResponse(exchange, 400, errBody); return;

                    }

                    HttpResponse<String> userResp = HttpUtils.sendGetRequest(iscsIp, iscsPort, "/user/"+req.getUserId());


                    // enough to check status
                    if (userResp.statusCode() != 200) {
                        // user doesn't exist
                        // 400 {}
                        ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                        errBody = gson.toJson(ordResp);
                        HttpUtils.sendHttpResponse(exchange, userResp.statusCode(), errBody); return;

                    }

                    HttpResponse<String> prodResp = HttpUtils.sendGetRequest(iscsIp, iscsPort, "/product/" + req.getProductId());

                    if (prodResp.statusCode() != 200) {
                        // prod doesnt exist or malformed 
                        // 400 {}
                        ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                        errBody = gson.toJson(ordResp);
                        HttpUtils.sendHttpResponse(exchange, prodResp.statusCode(), errBody); return;
                    }

                    // prdocut exists 
                    // need to check quantity
                    Product p = gson.fromJson(prodResp.body(), Product.class);
                    if (p.getQuantity() > req.getQuantity()) {
                        // enough quant
                        // place update order on product
                        ProductPostRequest prodUpdateReq = new ProductPostRequest("update", p.getId(), null, null, null, p.getQuantity() - req.getQuantity());
                        String prodUpdateReqBody = gson.toJson(prodUpdateReq);
                        HttpResponse<String> prodUpdateResp = HttpUtils.sendPostRequest(iscsIp, iscsPort, "/product", prodUpdateReqBody);

                        Product updatedProd = gson.fromJson(prodUpdateResp.body(), Product.class);

                        // construct order response
                        // when constructing the order response, i need to keep track of it in a db and update the id
                        ordResp = new OrderResponse(null, p.getId(), req.getUserId(), req.getQuantity(), "Success");
                        String respBody = gson.toJson(ordResp);

                        // Record the purchase for this user
                        userPurchases
                            .computeIfAbsent(req.getUserId(), k -> new HashMap<>())
                            .merge(req.getProductId(), req.getQuantity(), Integer::sum);

                        try (FileWriter writer = new FileWriter(purchasesDbPath)) {
                            gson.toJson(userPurchases, writer);
}

                        // 200 ok order response
                        HttpUtils.sendHttpResponse(exchange, 200, respBody); return;


                    } else {
                        // not enough quant
                        //
                        // 400 status - exceeded quant
                        ordResp = new OrderResponse(null, null, null, null, "Exceeded quantity limit");
                        String errorBody = gson.toJson(ordResp);
                        HttpUtils.sendHttpResponse(exchange, 400, errorBody); return;

                    }

                } catch (InterruptedException e) {
                    // return 500 {}
                    System.out.println("error: InterruptedException");
                    HttpUtils.sendHttpResponse(exchange, 500, "{}"); return;
                } catch (IOException e) {
                    // return 500 {}
                    System.out.println("error: IOException");
                    HttpUtils.sendHttpResponse(exchange, 500, "{}"); return;
                }

            } else {
                // command wrong i trhink
                // bad request 400
                ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                errBody = gson.toJson(ordResp);
                HttpUtils.sendHttpResponse(exchange, 400, "{'status': 'wrong command'}"); return;
            }

        } else {

            // not a post method
            ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
            errBody = gson.toJson(ordResp);
            HttpUtils.sendHttpResponse(exchange, 400, "{'status': 'not a post method'}"); return;

        }

        } catch (JsonSyntaxException e) {
            HttpUtils.sendHttpResponse(exchange, 400, "{'status': 'Invalid Request hell'}"); return;
        }


    }
    /**
     * Returns the purchase history for a given user.
     * Route: GET /user/purchased/{userId}
     *
     * @param exchange HttpExchange for sending the response
     * @param path     the full request URI path
     */
    private void _handleUserPurchased(HttpExchange exchange, String path) throws IOException {
        // Extract userId from path: /user/purchased/{userId}
        String[] parts = path.split("/");
        // Expected: ["", "user", "purchased", "{id}"]
        if (parts.length != 4) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        int userId;
        try {
            userId = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        // Verify user exists by querying UserService via ISCS
        try {
            HttpResponse<String> userResp = HttpUtils.sendGetRequest(iscsIp, iscsPort, "/user/" + userId);
            if (userResp.statusCode() != 200) {
                HttpUtils.sendHttpResponse(exchange, 404, "{}"); return;
            }
        } catch (InterruptedException e) {
            HttpUtils.sendHttpResponse(exchange, 500, "{}"); return;
        }

        // User exists — return their purchases (empty map is valid)
        Map<Integer, Integer> purchases = userPurchases.getOrDefault(userId, new HashMap<>());
        String body = gson.toJson(purchases);
        HttpUtils.sendHttpResponse(exchange, 200, body); return;
    }
    
    public static void main(String[] args) throws IOException {

        // get the config file path from user
        String configPath = args[0];

        // read from json file
        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map <String, ServerConfig> servers = gson.fromJson(new FileReader(configPath), type);

        // get the config of the current server
        ServerConfig config = servers.get(serverName);

        // get the config of the ISCS
        ServerConfig iscsConfig = servers.get("InterServiceCommunication");
        ServerConfig userConfig = servers.get("UserService");
        ServerConfig productConfig = servers.get("ProductService");


        OrderService service = new OrderService(config.ip, config.port, iscsConfig.ip, iscsConfig.port);

        service.start();
        //service.stop(5);
    }
}
