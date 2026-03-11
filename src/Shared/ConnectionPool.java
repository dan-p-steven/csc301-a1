package Shared;

import java.sql.*;
import java.util.concurrent.ArrayBlockingQueue;

public class ConnectionPool {

    private final ArrayBlockingQueue<Connection> pool;
    private final String jdbcUrl;
    private final String user;
    private final String password;

    public ConnectionPool(String jdbcUrl, String user, String password, int poolSize)
            throws SQLException {
        this.jdbcUrl  = jdbcUrl;
        this.user     = user;
        this.password = password;
        this.pool     = new ArrayBlockingQueue<>(poolSize);

        // pre-fill the pool with connections on startup
        for (int i = 0; i < poolSize; i++) {
            pool.add(DriverManager.getConnection(jdbcUrl, user, password));
        }
    }

    /**
     * Borrow a connection from the pool.
     * Blocks if all connections are in use until one is returned.
     */
    public Connection getConnection() 
    throws InterruptedException, SQLException {

        Connection conn = pool.take();

        // if connection is dead, replace it with a fresh one
        if (!conn.isValid(1)) {
            conn = DriverManager.getConnection(jdbcUrl, user, password);
        }

        return conn;

    }

    /**
     * Return a connection back to the pool after use.
     */
    public void releaseConnection(Connection conn) {
        pool.offer(conn);
    }
}
