package OrderService;

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

public class OrderService extends MicroService {

    private static String serverName = "OrderService";

    // allowed API endpoints
    private static String context = "/order";
    private static String[] allowedContexts = {"/user", "/product"};

    // "database" (temp memory)
    private List<Order> orders = new ArrayList<>();

    private static Gson gson = new Gson();

    // remember information about ISCS
    private String iscsIp;
    private int iscsPort;

    private int orderCount = 0;

    public OrderService (String ip, int port,
                        String iscsIp, int iscsPort) throws IOException {

        super(ip, port);
        addContext(context, new OrderHandler());

        this.iscsIp = iscsIp;
        this.iscsPort = iscsPort;

    }


    class OrderHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String path = exchange.getRequestURI().getPath();
            HttpResponse<String> resp;

            if (path.startsWith("/user") || path.startsWith("/product")) {
                // forward to icsc
                //
                try {

                    resp = HttpUtils.forwardRequest(exchange, iscsIp, iscsPort);
                    // forward response 
                    HttpUtils.forwardResponse(exchange, resp);

                } catch (InterruptedException e) {
                    HttpUtils.sendHttpResponse(exchange, 500, "{}");
                } catch (IOException e) {
                    HttpUtils.sendHttpResponse(exchange, 500, "{}");
                }

            } else if (path.startsWith("/order")) {
                // handle the /order endpoint here using a helper function
                _handleOrder(exchange);

            } else {
                // return error
                HttpUtils.sendHttpResponse(exchange, 400, "{}");
            }
        }
    }

    private void _handleOrder(HttpExchange exchange) throws IOException {

            String errBody;
            OrderResponse ordResp;
            String method = exchange.getRequestMethod();
            System.out.println(method);

            if (method.equals("POST")) {

                // convert to request object
                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");

                // try
                OrderRequest req = gson.fromJson(reader, OrderRequest.class);

                // extract command
                if (req.getCommand().equals("place order")) {
                    // verify if user id exists
                    //try
                    try {
                        HttpResponse<String> userResp = HttpUtils.sendGetRequest(iscsIp, iscsPort, "/user/"+req.getUserId());

                        // enough to check status
                        if (userResp.statusCode() != 200) {
                            // user doesn't exist
                            // 400 {}
                            ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                            errBody = gson.toJson(ordResp);
                            HttpUtils.sendHttpResponse(exchange, 400, errBody);

                        }

                        HttpResponse<String> prodResp = HttpUtils.sendGetRequest(iscsIp, iscsPort, "/product/" + req.getProductId());

                        if (prodResp.statusCode() != 200) {
                            // prod doesnt exist or malformed 
                            // 400 {}
                            ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                            errBody = gson.toJson(ordResp);
                            HttpUtils.sendHttpResponse(exchange, 400, errBody);
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
                            ordResp = new OrderResponse(orderCount, p.getId(), req.getUserId(), updatedProd.getQuantity(), "Success");
                            String respBody = gson.toJson(ordResp);

                            // 200 ok order response
                            HttpUtils.sendHttpResponse(exchange, 200, respBody);

                        } else {
                            // not enough quant
                            //
                            // 400 status - exceeded quant
                            ordResp = new OrderResponse(null, null, null, null, "Exceeded quantity limit");
                            String errorBody = gson.toJson(ordResp);
                            HttpUtils.sendHttpResponse(exchange, 400, errorBody);

                        }

                    } catch (InterruptedException e) {
                        // return 500 {}
                        // 
                        HttpUtils.sendHttpResponse(exchange, 500, "{}");
                    } catch (IOException e) {
                        // return 500 {}
                        HttpUtils.sendHttpResponse(exchange, 500, "{}");
                    }

                } else {
                    // not a POST
                    // bad request 400
                    ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                    errBody = gson.toJson(ordResp);
                    HttpUtils.sendHttpResponse(exchange, 400, errBody);
                }

            } else {
                // bad request 400
                ordResp = new OrderResponse(null, null, null, null, "Invalid Request");
                errBody = gson.toJson(ordResp);
                HttpUtils.sendHttpResponse(exchange, 400, errBody);

            }

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


        OrderService service = new OrderService(config.ip, config.port, iscsConfig.ip, iscsConfig.port);

        service.start();
        //service.stop(5);
    }
}
