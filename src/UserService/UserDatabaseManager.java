/*
 * Manages all database operations for the users table.
 *
 * Passwords are expected to be pre-hashed (SHA-256) by the service layer
 * before being passed here. This class never handles raw passwords.
 *
 * @author Daniel Steven
 */
package UserService;

import java.sql.*;

public class UserDatabaseManager {

    private final Connection conn;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public UserDatabaseManager(String jdbcUrl, String user, String password)
            throws SQLException {
        this.conn = DriverManager.getConnection(jdbcUrl, user, password);
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    id       INT          PRIMARY KEY,
                    username VARCHAR(255) NOT NULL,
                    email    VARCHAR(255) NOT NULL,
                    password VARCHAR(64)  NOT NULL
                )
                """;
        // password is VARCHAR(64) — the exact length of a hex-encoded SHA-256 hash
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // ------------------------------------------------------------------
    // INSERT
    // ------------------------------------------------------------------

    /**
     * Password must be pre-hashed by the caller before passing here.
     *
     * @return true on success, false if a user with that id already exists (HTTP 409)
     */
    public boolean insert(User u) throws SQLException {
        String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, u.getId());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getEmail());
            ps.setString(4, u.getPassword()); // already hashed
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
     * @return the User, or null if not found (HTTP 404)
     */
    public User getById(int id) throws SQLException {
        String sql = "SELECT id, username, email, password FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password")
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
     * Password must be pre-hashed by the caller before passing here.
     *
     * @return true if a row was updated, false if the id was not found (HTTP 404)
     */
    public boolean update(int id, String username, String email,
                          String hashedPassword) throws SQLException {

        StringBuilder sb = new StringBuilder("UPDATE users SET ");

        if (username       != null) sb.append("username = ?, ");
        if (email          != null) sb.append("email = ?, ");
        if (hashedPassword != null) sb.append("password = ?, ");

        // nothing to update
        if (sb.toString().equals("UPDATE users SET ")) return false;

        // trim trailing ", "
        sb.setLength(sb.length() - 2);
        sb.append(" WHERE id = ?");

        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            if (username       != null) ps.setString(idx++, username);
            if (email          != null) ps.setString(idx++, email);
            if (hashedPassword != null) ps.setString(idx++, hashedPassword);
            ps.setInt(idx, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------------

    /**
     * Deletes a user only if username, email, and hashed password all match
     * the stored row — mirrors the original validation logic.
     * Password must be pre-hashed by the caller before passing here.
     *
     * @return true if a row was deleted, false if no matching row was found (HTTP 404)
     */
    public boolean delete(int id, String username, String email,
                          String hashedPassword) throws SQLException {
        String sql = """
                DELETE FROM users
                WHERE id = ? AND username = ? AND email = ? AND password = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, hashedPassword);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------
    // WIPE
    // ------------------------------------------------------------------

    public void wipe() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM users");
        }
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }
}
