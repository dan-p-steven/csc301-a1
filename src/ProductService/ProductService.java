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
    private int DB_POOL = 3; // Added the connection pool baseline

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public ProductService(String ip, int port, String jdbcUrl, String dbUser, String dbPassword)
            throws IOException, SQLException, InterruptedException {
        super(ip, port);
        addContext(CONTEXT, new ProductHandler());
        this.db = new ProductDatabaseManager(jdbcUrl, dbUser, dbPassword, DB_POOL);
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

        Product newProduct = new Product(
            req.getId(), req.getName(), req.getDescription(),
            req.getPrice(), req.getQuantity()
        );

        db.insert(newProduct)
            .thenAccept(inserted -> {
                try {
                    if (!inserted) {
                        HttpUtils.sendHttpResponse(exchange, 409, "{}");
                    } else {
                        HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(newProduct));
                    }
                } catch (IOException ignored) {}
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                return null;
            });
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

        db.update(req.getId(), req.getName(), req.getDescription(), req.getPrice(), req.getQuantity())
            .thenCompose(updated -> {
                if (!updated) {
                    try { HttpUtils.sendHttpResponse(exchange, 404, "{}"); } catch (IOException ignored) {}
                    return java.util.concurrent.CompletableFuture.completedFuture((Product) null);
                }
                return db.getById(req.getId());
            })
            .thenAccept(updatedProduct -> {
                if (updatedProduct != null) {
                    try {
                        HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(updatedProduct));
                    } catch (IOException ignored) {}
                }
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                return null;
            });
    }

    public void deleteProduct(HttpExchange exchange, ProductPostRequest req) throws IOException {

        if (req.getId() == null || req.getName() == null
                || req.getPrice() == null || req.getQuantity() == null) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        db.delete(req.getId(), req.getName(), req.getPrice(), req.getQuantity())
            .thenAccept(deleted -> {
                try {
                    if (deleted) {
                        HttpUtils.sendHttpResponse(exchange, 200, "{}");
                    } else {
                        HttpUtils.sendHttpResponse(exchange, 404, "{}");
                    }
                } catch (IOException ignored) {}
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                return null;
            });
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

        db.getById(id)
            .thenAccept(p -> {
                try {
                    if (p != null) {
                        HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(p));
                    } else {
                        HttpUtils.sendHttpResponse(exchange, 404, "{}");
                    }
                } catch (IOException ignored) {}
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                return null;
            });
    }

    // ------------------------------------------------------------------
    // HTTP routing
    // ------------------------------------------------------------------

    class ProductHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            String path   = exchange.getRequestURI().getPath();

            // Made /wipe fully async as well
            if (path.equals("/product/wipe")) {
                db.wipe()
                    .thenAccept(v -> {
                        try { HttpUtils.sendHttpResponse(exchange, 200, "{}"); } catch (IOException ignored) {}
                    })
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                        return null;
                    });
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

    public static void main(String[] args) throws IOException, SQLException, InterruptedException {

        String configPath = args[0];

        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map<String, ServerConfig> servers =
            gson.fromJson(new FileReader(configPath), type);

        ServerConfig config = servers.get(SERVER_NAME);

        ProductService service = new ProductService(
            "0.0.0.0",
            config.port,
            config.db.url,
            config.db.user,
            config.db.password
        );

        service.start();
    }
}
