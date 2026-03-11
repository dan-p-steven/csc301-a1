package UserService;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import Shared.ConnectionPool;

public class UserDatabaseManager {

    private final Connection conn;
    private final ConnectionPool pool;
    private final ExecutorService dbExecutor;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    // Added poolSize and InterruptedException
    public UserDatabaseManager(String jdbcUrl, String user, String password, int poolSize)
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
                CREATE TABLE IF NOT EXISTS users (
                    id       INT          PRIMARY KEY,
                    username VARCHAR(255) NOT NULL,
                    email    VARCHAR(255) NOT NULL,
                    password VARCHAR(64)  NOT NULL
                )
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // ------------------------------------------------------------------
    // INSERT (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Boolean> insert(User u) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)";
            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, u.getId());
                    ps.setString(2, u.getUsername());
                    ps.setString(3, u.getEmail());
                    ps.setString(4, u.getPassword());
                    ps.executeUpdate();
                    return true;
                } catch (SQLException e) {
                    if ("23505".equals(e.getSQLState())) return false; // duplicate key
                    throw e; // rethrow to be caught by the outer catch block
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

    public CompletableFuture<User> getById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, username, email, password FROM users WHERE id = ?";
            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
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
                } finally {
                    pool.releaseConnection(c);
                }
            } catch (SQLException | InterruptedException e) {
                throw new CompletionException(e);
            }
            return null; // return null if not found
        }, dbExecutor);
    }

    // ------------------------------------------------------------------
    // UPDATE (Async)
    // ------------------------------------------------------------------

    public CompletableFuture<Boolean> update(int id, String username, String email,
                                             String hashedPassword) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder("UPDATE users SET ");

            if (username       != null) sb.append("username = ?, ");
            if (email          != null) sb.append("email = ?, ");
            if (hashedPassword != null) sb.append("password = ?, ");

            if (sb.toString().equals("UPDATE users SET ")) return false;

            sb.setLength(sb.length() - 2);
            sb.append(" WHERE id = ?");

            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
                    int idx = 1;
                    if (username       != null) ps.setString(idx++, username);
                    if (email          != null) ps.setString(idx++, email);
                    if (hashedPassword != null) ps.setString(idx++, hashedPassword);
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

    public CompletableFuture<Boolean> delete(int id, String username, String email,
                                             String hashedPassword) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                    DELETE FROM users
                    WHERE id = ? AND username = ? AND email = ? AND password = ?
                    """;
            try {
                Connection c = pool.getConnection();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    ps.setString(2, username);
                    ps.setString(3, email);
                    ps.setString(4, hashedPassword);
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
                    stmt.execute("DELETE FROM users");
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
