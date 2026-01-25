package UserService;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import Shared.MicroService;


public class UserService extends MicroService{

    private String context = "/user";

    public UserService (String ip, int port) throws IOException{

        super(ip, port);
        addContext(context, new UserHandler());


    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            System.out.println(method);
        }
    }

    public static void main(String[] args) throws IOException{

        String ip = "127.0.0.1";
        int port = 8000;

        UserService u = new UserService(ip, port);
        u.start();
        //u.stop(5);
    }

    //User updateUser(User user, int id, String email, String username, String password) {
    //    // Update  user with the new fields
    //    user.setId(id);
    //    user.setEmail(email);
    //    user.setUsername(username);
    //    user.setPassword(password);

    //    return user;
    //}

}
