/*
 * Manages all database operations for the products table.
 *
 * @author Daniel Steven
 */
package ProductService;

import java.sql.*;
import java.util.Optional;

public class ProductDatabaseManager {

    private final Connection conn;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public ProductDatabaseManager(String jdbcUrl, String user, String password)
            throws SQLException {
        this.conn = DriverManager.getConnection(jdbcUrl, user, password);
        this.conn.setAutoCommit(true); // add this
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
    // INSERT
    // ------------------------------------------------------------------

    /**
     * @return true on success, false if a product with that id already exists (HTTP 409)
     */
    public boolean insert(Product p) throws SQLException {
        String sql = "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
        }
    }

    // ------------------------------------------------------------------
    // SELECT
    // ------------------------------------------------------------------

    /**
     * @return the Product, or null if not found (HTTP 404)
     */
    public Product getById(int id) throws SQLException {
        String sql = "SELECT id, name, description, price, quantity FROM products WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
        }
        return null;
    }

    // ------------------------------------------------------------------
    // UPDATE (partial — only non-null fields are updated)
    // ------------------------------------------------------------------

    /**
     * Updates only the fields that are non-null in the request.
     * Use boxed types (Integer, Float) in ProductPostRequest so that
     * untouched fields can be null.
     *
     * @return true if a row was updated, false if the id was not found (HTTP 404)
     */
    public boolean update(int id, String name, String description,
                          Float price, Integer quantity) throws SQLException {

        StringBuilder sb = new StringBuilder("UPDATE products SET ");

        if (name        != null) sb.append("name = ?, ");
        if (description != null) sb.append("description = ?, ");
        if (price       != null) sb.append("price = ?, ");
        if (quantity    != null) sb.append("quantity = ?, ");

        // nothing to update
        if (sb.toString().equals("UPDATE products SET ")) return false;

        // trim trailing ", "
        sb.setLength(sb.length() - 2);
        sb.append(" WHERE id = ?");

        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            if (name        != null) ps.setString(idx++, name);
            if (description != null) ps.setString(idx++, description);
            if (price       != null) ps.setFloat(idx++, price);
            if (quantity    != null) ps.setInt(idx++, quantity);
            ps.setInt(idx, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------------

    /**
     * Deletes a product only if name, price, and quantity all match the stored
     * row — mirrors the original validation logic.
     *
     * @return true if a row was deleted, false if no matching row was found (HTTP 404)
     */
    public boolean delete(int id, String name, float price, int quantity) throws SQLException {
        String sql = """
                DELETE FROM products
                WHERE id = ? AND name = ? AND price = ? AND quantity = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setFloat(3, price);
            ps.setInt(4, quantity);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------
    // WIPE
    // ------------------------------------------------------------------

    public void wipe() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM products");
        }
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}
