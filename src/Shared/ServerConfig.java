package Shared;
import java.util.List;

public class ServerConfig {
    public List<String> ips;
    public int port;
    public DbConfig db;

    public static class DbConfig {
        public String url;
        public String user;
        public String password;
    }

}
