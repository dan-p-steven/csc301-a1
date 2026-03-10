/*
 * A class representing the core business and routing logic of the ProductService.
 *
 * @author Daniel Steven
 */
package ProductService;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.SQLException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import Shared.MicroService;
import Shared.HttpUtils;
import Shared.ServerConfig;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.Map;
import com.google.gson.reflect.TypeToken;

public class ProductService extends MicroService {

    private static final String SERVER_NAME = "ProductService";
    private static final String CONTEXT     = "/product";

    private static final Gson gson = new Gson();

    private final ProductDatabaseManager db;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public ProductService(String ip, int port, String jdbcUrl, String dbUser, String dbPassword)
            throws IOException, SQLException {
        super(ip, port);
        addContext(CONTEXT, new ProductHandler());
        this.db = new ProductDatabaseManager(jdbcUrl, dbUser, dbPassword);
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    private static boolean _invalid(String s) {
        return s == null || s.isBlank();
    }

    private static boolean invalidStrings(ProductPostRequest req) {
        return _invalid(req.getName()) || _invalid(req.getDescription());
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    public void createProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {

        if (req.getId() == null || invalidStrings(req)
                || req.getPrice() == null || req.getQuantity() == null) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        if (req.getQuantity() < 0 || req.getPrice() < 0) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        try {
            Product newProduct = new Product(
                req.getId(), req.getName(), req.getDescription(),
                req.getPrice(), req.getQuantity()
            );

            boolean inserted = db.insert(newProduct);

            if (!inserted) {
                HttpUtils.sendHttpResponse(exchange, 409, "{}"); return;
            }

            HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(newProduct));

        } catch (SQLException e) {
            e.printStackTrace();
            HttpUtils.sendHttpResponse(exchange, 500, "{}");
        }
    }

    public void updateProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {

        if (req.getId() == null) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        if ((req.getName() != null && req.getName().isBlank())
                || (req.getDescription() != null && req.getDescription().isBlank())) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        if ((req.getPrice() != null && req.getPrice() < 0)
                || (req.getQuantity() != null && req.getQuantity() < 0)) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        try {
            boolean updated = db.update(
                req.getId(), req.getName(), req.getDescription(),
                req.getPrice(), req.getQuantity()
            );

            if (!updated) {
                HttpUtils.sendHttpResponse(exchange, 404, "{}"); return;
            }

            // re-fetch the updated row to return it in the response
            Product updatedProduct = db.getById(req.getId());
            HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(updatedProduct));

        } catch (SQLException e) {
            e.printStackTrace();
            HttpUtils.sendHttpResponse(exchange, 500, "{}");
        }
    }

    public void deleteProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {

        if (req.getId() == null || req.getName() == null
                || req.getPrice() == null || req.getQuantity() == null) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        try {
            boolean deleted = db.delete(
                req.getId(), req.getName(), req.getPrice(), req.getQuantity()
            );

            if (deleted) {
                HttpUtils.sendHttpResponse(exchange, 200, "{}");
            } else {
                HttpUtils.sendHttpResponse(exchange, 404, "{}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            HttpUtils.sendHttpResponse(exchange, 500, "{}");
        }
    }

    public void getProduct(HttpExchange exchange, String path) throws IOException {

        String[] splitPath = path.split("/");
        String   query     = exchange.getRequestURI().getQuery();
        int      id        = -1;

        if (query != null && query.startsWith("id=")) {
            try {
                id = Integer.parseInt(query.split("=")[1]);
            } catch (NumberFormatException e) {
                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }
        } else if (splitPath.length == 3) {
            try {
                id = Integer.parseInt(splitPath[2]);
            } catch (Exception e) {
                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }
        } else {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        try {
            Product p = db.getById(id);

            if (p != null) {
                HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(p));
            } else {
                HttpUtils.sendHttpResponse(exchange, 404, "{}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            HttpUtils.sendHttpResponse(exchange, 500, "{}");
        }
    }

    // ------------------------------------------------------------------
    // HTTP routing
    // ------------------------------------------------------------------

    class ProductHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            String path   = exchange.getRequestURI().getPath();

            if (path.equals("/product/wipe")) {
                try {
                    db.wipe();
                    HttpUtils.sendHttpResponse(exchange, 200, "{}");
                } catch (SQLException e) {
                    e.printStackTrace();
                    HttpUtils.sendHttpResponse(exchange, 500, "{}");
                }
                return;
            }

            if (path.equals("/product/shutdown")) {
                HttpUtils.sendHttpResponse(exchange, 200, "{}");
                new Thread(() -> {
                    try { Thread.sleep(500); System.exit(0); }
                    catch (Exception e) {}
                }).start();
                return;
            }

            switch (method) {
                case "POST":
                    InputStreamReader reader =
                        new InputStreamReader(exchange.getRequestBody(), "UTF-8");
                    try {
                        ProductPostRequest req = gson.fromJson(reader, ProductPostRequest.class);
                        switch (req.getCommand()) {
                            case "create": createProduct(exchange, req); break;
                            case "update": updateProduct(exchange, req); break;
                            case "delete": deleteProduct(exchange, req); break;
                            default:
                                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
                        }
                    } catch (JsonSyntaxException e) {
                        HttpUtils.sendHttpResponse(exchange, 400, "{}");
                    }
                    break;

                case "GET":
                    getProduct(exchange, path);
                    break;

                default:
                    HttpUtils.sendHttpResponse(exchange, 400, "{}");
            }
        }
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static void main(String[] args) throws IOException, SQLException {

        String configPath = args[0];

        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map<String, ServerConfig> servers =
            gson.fromJson(new FileReader(configPath), type);

        ServerConfig config = servers.get(SERVER_NAME);

        ProductService service = new ProductService(
            config.ip,
            config.port,
            config.db.url,
            config.db.user,
            config.db.password
        );

        service.start();
    }
}
