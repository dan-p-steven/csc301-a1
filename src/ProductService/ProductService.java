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

public class ProductService extends MicroService{

    private static String serverName = "ProductService";

    // API endpoint
    private static String context = "/product";

    // "database" (temp memory)
    private List<Product> products = new ArrayList<>();

    private static Gson gson = new Gson();

    public ProductService (String ip, int port) throws IOException{

        super(ip, port);
        addContext(context, new ProductHandler());


    }

    public void createProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {
        // create a product
        //
        // all fields must be required.
        if (req.getId() == null || req.getName() == null || req.getDescription() == null || req.getPrice() == null || req.getQuantity() == null) {

            // return 400 error empty data
            HttpUtils.sendHttpResponse(exchange, 400, "{}");

        } else {

            if (req.getQuantity() < 0 || req.getPrice() < 0) {
                // return 400 error
                HttpUtils.sendHttpResponse(exchange, 400, "{}");
            }

            // check for duplicate products
            for (Product p: this.products) {
                if (p.getId() == req.getId()) {
                    // found duplicate product
                    // return 409
                    HttpUtils.sendHttpResponse(exchange, 409, "{}");
                }
            }

            // no dupes, is success
            // return 200 and product object
            Product newProduct = new Product(req.getId(), req.getName(), req.getDescription(), req.getPrice(), req.getQuantity());

            // add new product to aatabase
            this.products.add(newProduct);

            // convert to json
            String data = gson.toJson(newProduct);

            HttpUtils.sendHttpResponse(exchange, 200, data);

        }
    }

    //public void updateUser(HttpExchange exchange, ProductPostRequest req) throws IOException {
    //    for (User u : this.users) {

    //        if (u.getId() == req.getId()) {

    //            // update fields
    //            if (req.getUsername() != null) {
    //                u.setUsername(req.getUsername());
    //            }

    //            if (req.getEmail() != null) {
    //                u.setEmail(req.getEmail());
    //            }

    //            if (req.getPassword() != null) {
    //                u.setPassword(SecurityUtils.SHA256Hash(req.getPassword()));
    //            }

    //            // success
    //            String data = gson.toJson(u);
    //            HttpUtils.sendHttpResponse(exchange, 200, data);
    //        }
    //    }

    //    // req not in list
    //    // return 400 {}
    //    HttpUtils.sendHttpResponse(exchange, 400, "{}");
    //}

    //public void deleteUser(HttpExchange exchange, ProductPostRequest req) throws IOException {

    //    if (req.getId() == null || req.getEmail() == null || req.getUsername() == null || req.getPassword() == null ) {
    //        // a value was null
    //        HttpUtils.sendHttpResponse(exchange, 400, "{}");

    //    } else {
    //        // not empty request, need to ensure user exists
    //        for (User u : this.users) {
    //            if (u.getId() == req.getId()) {
    //                // found match
    //                // need to validate values
    //                if (u.getEmail().equals(req.getEmail()) &&
    //                    u.getUsername().equals(req.getUsername()) && u.getPassword().equals(SecurityUtils.SHA256Hash(req.getPassword()))) {
    //                    // valid match
    //                    // delete u from users, return success
    //                    this.users.remove(u);
    //                    HttpUtils.sendHttpResponse(exchange, 200, "{}");

    //                } else {
    //                    // invalid match
    //                    HttpUtils.sendHttpResponse(exchange, 400, "{}");

    //                }
    //            }
    //        }

    //        // user id DNE
    //        HttpUtils.sendHttpResponse(exchange, 400, "{}");
    //    }
    //}

    class ProductHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            System.out.println(method);

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
                                //updateUser(exchange, req);
                                break;

                            case "delete":

                                System.out.println("Delete command detected!");
                                //deleteUser(exchange, req);
                                break;

                            default:

                                // unknown post request, return some kind of error
                                HttpUtils.sendHttpResponse(exchange, 400, "{}");
                                break;
                        }
                    } catch (JsonSyntaxException e) {
                        // malformed json body
                        HttpUtils.sendHttpResponse(exchange, 400, "{}");
                    }
                    break;

                case "GET":
                    //getUser(exchange, exchange.getRequestURI().getPath());

                default:
                    // unknown http request method
                    HttpUtils.sendHttpResponse(exchange, 400, "{}");
                    break;
            }
        }
    }

    //public void getUser(HttpExchange exchange, String path) throws IOException {

    //    String[] splitPath = path.split("/");
    //    String contextValue = context.split("/")[1];
    //    System.out.println(contextValue);

    //    if (splitPath.length != 3) {
    //        // fail, 400 {}
    //        System.out.println("url path length not 2: " + splitPath.length);
    //        HttpUtils.sendHttpResponse(exchange, 400, "{}");

    //    } else if (splitPath[0].equals("user")) {
    //       // fail  400 {}
    //       System.out.println("first url path not 'user'");
    //       HttpUtils.sendHttpResponse(exchange, 400, "{}");

    //    } else {

    //        // check if user id is a valid int in user database 
    //        try {

    //            // try to turn id into int
    //            int id = Integer.parseInt(splitPath[2]);
    //            System.out.println(id);

    //            // check if we have user with that id in db
    //            for (User u: this.users) {
    //                if (u.getId() == id) {

    //                    // success, 200 and user
    //                    String data = gson.toJson(u);
    //                    HttpUtils.sendHttpResponse(exchange, 200, data);

    //                }
    //            }

    //        } catch (NumberFormatException e) {

    //            // there are letters in id , 400 {}
    //            System.out.println("id not numeric");
    //            HttpUtils.sendHttpResponse(exchange, 400, "{}");
    //        }
    //    }

    //    // user not found, return 404
    //    HttpUtils.sendHttpResponse(exchange, 404, "{}");
    //}

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
        //u.stop(5);
    }
}
