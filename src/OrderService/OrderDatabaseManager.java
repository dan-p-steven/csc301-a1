package OrderService;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import Shared.ConnectionPool;

public class OrderDatabaseManager {

    private final Connection conn;
    private final ConnectionPool pool;
    
    // 1. Create a dedicated thread pool for database operations
    private final ExecutorService dbExecutor;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public OrderDatabaseManager(String jdbcUrl, String user, String password, int poolSize)
            throws SQLException, InterruptedException {

        this.conn = DriverManager.getConnection(jdbcUrl, user, password);
        this.pool = new ConnectionPool(jdbcUrl, user, password, poolSize);
        this.conn.setAutoCommit(true);
        
        // Match the executor thread count to the connection pool size
        this.dbExecutor = Executors.newFixedThreadPool(poolSize);
        
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS purchases (
                    user_id    INT NOT NULL,
                    product_id INT NOT NULL,
                    quantity   INT NOT NULL,
                    PRIMARY KEY (user_id, product_id)
                )
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // ------------------------------------------------------------------
    // RECORD PURCHASE (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Void> recordPurchase(int userId, int productId, int quantity) {
        // runAsync is used because we don't need to return any data (Void)
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO purchases (user_id, product_id, quantity)
                    VALUES (?, ?, ?)
                    ON CONFLICT (user_id, product_id)
                    DO UPDATE SET quantity = purchases.quantity + EXCLUDED.quantity
                    """;
                    
            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, productId);
                    ps.setInt(3, quantity);
                    ps.executeUpdate();
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                // Java lambdas cannot throw checked exceptions, so we wrap them
                throw new CompletionException(e);
            }
        }, dbExecutor); // Pass the dedicated DB thread pool here!
    }

    // ------------------------------------------------------------------
    // GET PURCHASES FOR USER (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Map<Integer, Integer>> getPurchasesByUser(int userId) {
        // supplyAsync is used because we are returning data (the Map)
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT product_id, quantity FROM purchases WHERE user_id = ?";
            Map<Integer, Integer> purchases = new HashMap<>();
            
            try {
                // FIX: Switched to using the connection pool for thread safety
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            purchases.put(rs.getInt("product_id"), rs.getInt("quantity"));
                        }
                    }
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
            return purchases;
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
                    stmt.execute("DELETE FROM purchases");
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, dbExecutor);
    }

    // ------------------------------------------------------------------
    // CLOSE
    // ------------------------------------------------------------------

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
        dbExecutor.shutdown(); // Shut down the thread pool when closing
    }
}
