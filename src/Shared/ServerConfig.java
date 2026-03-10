package Shared;

public class ServerConfig {
    public String ip;
    public int port;
    public DbConfig db;

    public static class DbConfig {
        public String url;
        public String user;
        public String password;
    }

}

