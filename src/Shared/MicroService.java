/**
 * parent class containing the fundamental design of a microservice
 *
 * @author Daniel Steven
 */
package Shared;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MicroService {

    private HttpServer server;

    // Constructor for the microserver. Needs an address and ip to construct.
    public MicroService(String ip, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        server.setExecutor(null); // default executor
    }

    // Add a context for the micro server. We want this to be variable because
    // different services will have different API endpoints.
    public void addContext(String context, HttpHandler handler) {
        server.createContext(context, handler);
    }

    // Start the server.
    public void start() {
        server.start();
        System.out.println("Server started at http://" + server.getAddress().getHostString() 
                           + ":" + server.getAddress().getPort());
    }

    // Stop the server.
    public void stop(int delay) {
        server.stop(delay);
        System.out.println("Server stopped at http://" + server.getAddress().getHostString() 
                           + ":" + server.getAddress().getPort());
    }

}
