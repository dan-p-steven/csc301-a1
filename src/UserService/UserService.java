package UserService;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import Shared.MicroService;
import Shared.User;


public class UserService extends MicroService{

    // API endpoint
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

            switch (method) {
                case "POST":

                    InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
                    Gson gson = new Gson();

                    User user = gson.fromJson(reader, User.class);
                    System.out.println(user.getId());
                    System.out.println(user.getUsername());
                    System.out.println(user.getEmail());
                    System.out.println(user.getPassword());
                    break;

                case "GET":
                default:
                    break;
            }



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
