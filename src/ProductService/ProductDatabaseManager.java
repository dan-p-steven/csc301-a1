package ProductService;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import Shared.ConnectionPool;

public class ProductDatabaseManager {

    private final Connection conn;
    private final ConnectionPool pool;
    private final ExecutorService dbExecutor;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public ProductDatabaseManager(String jdbcUrl, String user, String password, int poolSize)
            throws SQLException, InterruptedException {
        this.conn = DriverManager.getConnection(jdbcUrl, user, password);
        this.conn.setAutoCommit(true);
        
        // Initialize the connection pool and matching thread pool
        this.pool = new ConnectionPool(jdbcUrl, user, password, poolSize);
        this.dbExecutor = Executors.newFixedThreadPool(poolSize);
        
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS products (
                    id          INT              PRIMARY KEY,
                    name        VARCHAR(255)     NOT NULL,
                    description TEXT             NOT NULL,
                    price       DOUBLE PRECISION NOT NULL,
                    quantity    INT              NOT NULL
                )
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // ------------------------------------------------------------------
    // INSERT (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Boolean> insert(Product p) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, p.getId());
                    ps.setString(2, p.getName());
                    ps.setString(3, p.getDescription());
                    ps.setFloat(4, p.getPrice());
                    ps.setInt(5, p.getQuantity());
                    ps.executeUpdate();
                    return true;
                } catch (SQLException e) {
                    if ("23505".equals(e.getSQLState())) return false; // duplicate key
                    throw e;
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, dbExecutor);
    }

    // ------------------------------------------------------------------
    // SELECT (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Product> getById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, name, description, price, quantity FROM products WHERE id = ?";
            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return new Product(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getFloat("price"),
                                rs.getInt("quantity")
                            );
                        }
                    }
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
            return null;
        }, dbExecutor);
    }

    // ------------------------------------------------------------------
    // UPDATE (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Boolean> update(int id, String name, String description,
                                             Float price, Integer quantity) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder("UPDATE products SET ");

            if (name        != null) sb.append("name = ?, ");
            if (description != null) sb.append("description = ?, ");
            if (price       != null) sb.append("price = ?, ");
            if (quantity    != null) sb.append("quantity = ?, ");

            if (sb.toString().equals("UPDATE products SET ")) return false;

            sb.setLength(sb.length() - 2);
            sb.append(" WHERE id = ?");

            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
                    int idx = 1;
                    if (name        != null) ps.setString(idx++, name);
                    if (description != null) ps.setString(idx++, description);
                    if (price       != null) ps.setFloat(idx++, price);
                    if (quantity    != null) ps.setInt(idx++, quantity);
                    ps.setInt(idx, id);
                    return ps.executeUpdate() > 0;
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, dbExecutor);
    }

    // ------------------------------------------------------------------
    // DELETE (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Boolean> delete(int id, String name, float price, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    DELETE FROM products
                    WHERE id = ? AND name = ? AND price = ? AND quantity = ?
                    """;
            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    ps.setString(2, name);
                    ps.setFloat(3, price);
                    ps.setInt(4, quantity);
                    return ps.executeUpdate() > 0;
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, dbExecutor);
    }

    // ------------------------------------------------------------------
    // WIPE (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Void> wipe() {
        return CompletableFuture.runAsync(() -> {
            try {
                Connection c = pool.getConnection();
                try (Statement stmt = c.createStatement()) {
                    stmt.execute("DELETE FROM products");
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, dbExecutor);
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
        if (dbExecutor != null) dbExecutor.shutdown();
    }
}
