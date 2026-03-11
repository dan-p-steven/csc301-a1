/*
 * Manages all database operations for the purchases table.
 *
 * Tracks how much of each product each user has purchased.
 * Schema: (user_id, product_id) is the composite primary key,
 * quantity is accumulated via INSERT ... ON CONFLICT ... DO UPDATE.
 *
 * @author Daniel Steven
 */
package OrderService;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class OrderDatabaseManager {

    private final Connection conn;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public OrderDatabaseManager(String jdbcUrl, String user, String password)
            throws SQLException {
        this.conn = DriverManager.getConnection(jdbcUrl, user, password);
        this.conn.setAutoCommit(true);
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
    // RECORD PURCHASE
    // ------------------------------------------------------------------

    /**
     * Records a purchase, accumulating quantity if the user has bought
     * this product before.
     *
     * Uses INSERT ... ON CONFLICT ... DO UPDATE so it works as both an
     * insert (first purchase) and an update (repeat purchase) in one query.
     */
    public void recordPurchase(int userId, int productId, int quantity) throws SQLException {
        String sql = """
                INSERT INTO purchases (user_id, product_id, quantity)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, product_id)
                DO UPDATE SET quantity = purchases.quantity + EXCLUDED.quantity
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // GET PURCHASES FOR USER
    // ------------------------------------------------------------------

    /**
     * Returns all purchases for a given user as a map of productId -> totalQuantity.
     * Returns an empty map if the user has no purchases.
     */
    public Map<Integer, Integer> getPurchasesByUser(int userId) throws SQLException {
        String sql = "SELECT product_id, quantity FROM purchases WHERE user_id = ?";
        Map<Integer, Integer> purchases = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purchases.put(rs.getInt("product_id"), rs.getInt("quantity"));
                }
            }
        }
        return purchases;
    }

    // ------------------------------------------------------------------
    // WIPE
    // ------------------------------------------------------------------

    public void wipe() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM purchases");
        }
    }

    // ------------------------------------------------------------------
    // CLOSE
    // ------------------------------------------------------------------

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}
