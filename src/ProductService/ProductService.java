/*
 * A class representing the core business and routing logic of the ProductService.
 *
 * @author Daniel Steven
 */
package ProductService;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.InputStreamReader;
import java.io.ObjectInputStream.GetField;
import java.security.Security;
import java.util.ArrayList;
import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import ProductService.Product;
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

import Shared.ScuffedDatabase;

public class ProductService extends MicroService{

    private static String serverName = "ProductService";
    private static String dbPath = "data/products.json";

    // API endpoint
    private static String context = "/product";

    // "database" (temp memory)
    private ArrayList<Product> products;

    private static Gson gson = new Gson();

    public ProductService (String ip, int port) throws IOException{

        super(ip, port);
        addContext(context, new ProductHandler());

        // Load users from file (or get empty ArrayList if file doesn't exist)
        Type prodListTpye = new TypeToken<ArrayList<Product>>(){}.getType();
        this.products = ScuffedDatabase.readFromFile(dbPath, prodListTpye);

    }

    // helper function to check if a string is valid (not empty or blank space or null)
    private static boolean _invalid(String s) {
        return s == null || s.isBlank();
    }

    // check if a product post request is invalid
    private static boolean invalid_strings(ProductPostRequest req) {
        if (_invalid(req.getName()) || _invalid(req.getDescription())) {
            return true;
        } else { return false; }
    }

    /** 
     * Create a new product given a POST request containing user information.
     *
     * @param HttpExchange to send the response
     * @param object containing fields of product to create
    */
    public void createProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {
        // create a product
        //
        // all fields must be required.
        if (req.getId() == null || invalid_strings(req) || req.getPrice() == null || req.getQuantity() == null) {

            // return 400 error empty data
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;

        } else {

            if (req.getQuantity() < 0 || req.getPrice() < 0) {
                // return 400 error
                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }

            // check for duplicate products
            for (Product p: this.products) {
                if (p.getId() == req.getId()) {
                    // found duplicate product
                    // return 409
                    HttpUtils.sendHttpResponse(exchange, 409, "{}"); return;
                }
            }

            // no dupes, is success
            // return 200 and product object
            Product newProduct = new Product(req.getId(), req.getName(), req.getDescription(), req.getPrice(), req.getQuantity());

            // add new product to aatabase
            this.products.add(newProduct);

            // write to file
            ScuffedDatabase.writeToFile(this.products, dbPath);

            // convert to json
            String data = gson.toJson(newProduct);

            HttpUtils.sendHttpResponse(exchange, 200, data); return;

        }
    }

    /** 
     * Update the information of a product
     *
     * @param HttpExchange to send the response
     * @param object containing the information of the requested product
     */
    public void updateProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {


        // check if the id is null
        if (req.getId() == null) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        System.out.println("desc: " + req.getDescription());
        // can not have a blank/empty string name or description

        if ((req.getName() != null && req.getName().isBlank()) || (req.getDescription() != null && req.getDescription().isBlank())) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        // negative price not allowed
        if ((req.getPrice() != null && req.getPrice() < 0) || (req.getQuantity() != null && req.getQuantity() < 0)) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        for (Product p : this.products) {

            if (p.getId() == req.getId()) {

                // update fields
                if (req.getName() != null) {
                    p.setName(req.getName());
                }

                if (req.getDescription() != null) {
                    p.setDescription(req.getDescription());
                }

                if (req.getPrice() != null) {
                    p.setPrice(req.getPrice());
                }

                if (req.getQuantity() != null) {
                    p.setQuantity(req.getQuantity());
                }

                // success , write updated db to file
                ScuffedDatabase.writeToFile(this.products, dbPath);
                String data = gson.toJson(p);
                HttpUtils.sendHttpResponse(exchange, 200, data); return;
            }
        }

        // req not in list
        // return 400 {}
        HttpUtils.sendHttpResponse(exchange, 404, "{}"); return;
    }

    /** 
     * Delete a product form the database.
     *
     * @param httpexchange object for response 
     * @param object containing information about the product to delete
     */
    public void deleteProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {


        if (req.getId() == null || req.getName() == null || req.getPrice() == null || req.getQuantity() == null) {
            // a value was null
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;

        } else {
            System.out.println("["+ req.getId() + "]");
            // not empty request, need to ensure user exists
            for (Product p : this.products) {
                System.out.println("\t"+ p.getId());
                if (p.getId() == req.getId()) {
                    // found match
                    // need to validate values
                    if (p.getName().equals(req.getName()) &&
                        p.getPrice() == req.getPrice() &&
                        p.getQuantity() == req.getQuantity() ) {
                        // valid match
                        // delete u from users, return success
                        this.products.remove(p);
                        ScuffedDatabase.writeToFile(this.products, dbPath);
                        HttpUtils.sendHttpResponse(exchange, 200, "{}"); return;

                    } else {
                        // invalid match
                        HttpUtils.sendHttpResponse(exchange, 404, "{}"); return;

                    }
                }
            }

            // user id DNE
            HttpUtils.sendHttpResponse(exchange, 404, "{}"); return;
        }
    }

    /**
     * Implementation of HttpHandler to route requests and perform business logic 
     */
    class ProductHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            System.out.println(method);

            if (path.equals("/product/wipe")) {
                products.clear();
                ScuffedDatabase.writeToFile(products, dbPath);
                HttpUtils.sendHttpResponse(exchange, 200, "{}");
                return;
            }

            if (path.equals("/product/shutdown")) {
                HttpUtils.sendHttpResponse(exchange, 200, "{}");
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        System.exit(0);
                    } catch (Exception e) {}
                }).start();
                return;
            }

            switch (method) {
                case "POST":

                    InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");

                    try {
                        ProductPostRequest req = gson.fromJson(reader, ProductPostRequest.class);

                        switch (req.getCommand()) {

                            case "create":

                                System.out.println("Create command detected!");
                                createProduct(exchange, req);
                                break;

                            case "update":

                                System.out.println("Update command detected!");
                                updateProduct(exchange, req);
                                break;

                            case "delete":

                                System.out.println("Delete command detected!" + req.getId());
                                deleteProduct(exchange, req);
                                break;

                            default:

                                // unknown post request, return some kind of error
                                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
                        }
                    } catch (JsonSyntaxException e) {
                        // malformed json body
                        HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
                    }

                    break;

                case "GET":
                    getProduct(exchange, exchange.getRequestURI().getPath());
                    break;
                default:
                    // unknown http request method
                    HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }
        }
    }

    /**
     * Retrieve information about a product based on their id
     *
     * @param HttpExchange object to send back a response
     * @param api enpoint path of which product to get
     */
    public void getProduct(HttpExchange exchange, String path) throws IOException {

        String[] splitPath = path.split("/");
        String query = exchange.getRequestURI().getQuery();

        int id = -1;

        if (query != null && query.startsWith("id=")) {
            try {
                id = Integer.parseInt(query.split("=")[1]);
            } catch (NumberFormatException e) {
                System.out.println("Error: query param id not numeric");
                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }
        } else if (splitPath.length == 3) {
            try {
                id = Integer.parseInt(splitPath[2]);
            } catch (Exception e) {
                System.out.println("Path id not numeric");
                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }
        } else {

            // neither format matched, return error.
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        for (Product p: this.products) {
            // if user found
            if (p.getId() == id) {
                String data = gson.toJson(p);
                HttpUtils.sendHttpResponse(exchange, 200, data); return;
            }
        }

        // product not found
        HttpUtils.sendHttpResponse(exchange, 404, "{}"); return;
    }

    public static void main(String[] args) throws IOException{

        // get the config file path from user
        String configPath = args[0];

        // read from json file
        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map <String, ServerConfig> servers = gson.fromJson(new FileReader(configPath), type);

        // get the config of the current server
        ServerConfig config = servers.get(serverName);

        ProductService service = new ProductService(config.ip, config.port);
        service.start();
        //service.stop(5);
    }
}
