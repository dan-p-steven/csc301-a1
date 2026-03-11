/*
 * A class representing the core business and routing logic of the UserService.
 *
 * @author Daniel Steven
 */
package UserService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.SQLException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import Shared.MicroService;
import Shared.SecurityUtils;
import Shared.HttpUtils;
import Shared.ServerConfig;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.Map;
import com.google.gson.reflect.TypeToken;

public class UserService extends MicroService {

    private static final String SERVER_NAME = "UserService";
    private static final String CONTEXT     = "/user";

    static Gson gson = new GsonBuilder()
        .registerTypeAdapter(String.class, new TypeAdapter<String>() {
            @Override
            public String read(JsonReader in) throws IOException {
                if (in.peek() != JsonToken.STRING) {
                    throw new JsonParseException(
                        "Expected a string but got: " + in.peek()
                    );
                }
                return in.nextString();
            }
            @Override
            public void write(JsonWriter out, String value) throws IOException {
                out.value(value);
            }
        })
        .create();

    private final UserDatabaseManager db;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public UserService(String ip, int port, String jdbcUrl, String dbUser, String dbPassword)
            throws IOException, SQLException, InterruptedException {
        super(ip, port);
        addContext(CONTEXT, new UserHandler());
        this.db = new UserDatabaseManager(jdbcUrl, dbUser, dbPassword, 120);
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    private static boolean _invalid(String s) {
        return s == null || s.isBlank();
    }

    private static boolean invalid(UserPostRequest req) {
        if (req.getId() == null) {
            return false;
        }
        return _invalid(req.getEmail()) || _invalid(req.getUsername()) || _invalid(req.getPassword());
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------
    public void createUser(HttpExchange exchange, UserPostRequest req) throws IOException {

        if (invalid(req)) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        // Hash the password on the HTTP thread before passing it to the DB thread
        User newUser = new User(
            req.getId(), req.getUsername(), req.getEmail(),
            SecurityUtils.SHA256Hash(req.getPassword())
        );

        // Call the async database method
        db.insert(newUser)
            .thenAccept(inserted -> {
                try {
                    if (!inserted) {
                        HttpUtils.sendHttpResponse(exchange, 409, "{}");
                    } else {
                        HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(newUser));
                    }
                } catch (IOException ignored) {}
            })
            .exceptionally(ex -> {
                // Catch any SQL or CompletionExceptions from the database thread
                ex.printStackTrace();
                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                return null;
            });
    }

    public void deleteUser(HttpExchange exchange, UserPostRequest req) throws IOException {

        if (req.getId() == null || req.getEmail() == null
                || req.getUsername() == null || req.getPassword() == null) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        // Hash the password synchronously
        String hashedPassword = SecurityUtils.SHA256Hash(req.getPassword());

        db.delete(req.getId(), req.getUsername(), req.getEmail(), hashedPassword)
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

    public void updateUser(HttpExchange exchange, UserPostRequest req) throws IOException {

        if (req.getId() == null) {
            HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
        }

        try {
            if (req.getUsername().isBlank() || req.getEmail().isBlank() || req.getPassword().isBlank()) {
                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
            }
        } catch (Exception e) {
            // fields were null — that's fine, null fields are just skipped in update
        }

        // Hash the password synchronously before starting the DB chain
        String hashedPassword = req.getPassword() != null
            ? SecurityUtils.SHA256Hash(req.getPassword())
            : null;

        // 1. Fire the async update
        db.update(req.getId(), req.getUsername(), req.getEmail(), hashedPassword)
            .thenCompose(updated -> {
                if (!updated) {
                    try { HttpUtils.sendHttpResponse(exchange, 404, "{}"); } catch (IOException ignored) {}
                    // Stop the chain by returning a completed future containing null
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                
                // 2. If update succeeded, chain the async fetch
                return db.getById(req.getId());
            })
            .thenAccept(updatedUser -> {
                // 3. Handle the final fetched user
                if (updatedUser != null) {
                    try {
                        HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(updatedUser));
                    } catch (IOException ignored) {}
                }
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                try { HttpUtils.sendHttpResponse(exchange, 500, "{}"); } catch (IOException ignored) {}
                return null;
            });
    }


public void getUser(HttpExchange exchange, String path) throws IOException {

        String[] splitPath = path.split("/");
        String   query     = exchange.getRequestURI().getQuery();
        int      id        = -1;

        // URL parsing remains unchanged
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

        // Call the async database method
        db.getById(id)
            .thenAccept(u -> {
                try {
                    if (u != null) {
                        HttpUtils.sendHttpResponse(exchange, 200, gson.toJson(u));
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

    class UserHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            String path   = exchange.getRequestURI().getPath();

            if (path.equals("/user/shutdown")) {
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
                        UserPostRequest req = gson.fromJson(reader, UserPostRequest.class);
                        switch (req.getCommand()) {
                            case "create": createUser(exchange, req); break;
                            case "update": updateUser(exchange, req); break;
                            case "delete": deleteUser(exchange, req); break;
                            default:
                                HttpUtils.sendHttpResponse(exchange, 400, "{}"); return;
                        }
                    } catch (JsonSyntaxException e) {
                        HttpUtils.sendHttpResponse(exchange, 400, "{}");
                    } catch (JsonParseException e) {
                        HttpUtils.sendHttpResponse(exchange, 400, "{}");
                    }
                    break;

                case "GET":
                    getUser(exchange, path);
                    break;

                default:
                    HttpUtils.sendHttpResponse(exchange, 400, "{}");
            }
        }
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static void main(String[] args) 
    throws IOException, SQLException, InterruptedException {

        String configPath = args[0];

        Type type = new TypeToken<Map<String, ServerConfig>>() {}.getType();
        Map<String, ServerConfig> servers =
            gson.fromJson(new FileReader(configPath), type);

        ServerConfig config = servers.get(SERVER_NAME);

        UserService service = new UserService(
            config.ip,
            config.port,
            config.db.url,
            config.db.user,
            config.db.password
        );

        service.start();
    }
}
