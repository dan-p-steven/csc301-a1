package UserService;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.ObjectInputStream.GetField;
import java.security.Security;
import java.util.ArrayList;
import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import Shared.MicroService;
import Shared.SecurityUtils;

import java.util.ArrayList;
import java.util.List;


public class UserService extends MicroService{

    // API endpoint
    private String context = "/user";

    // "database" (temp memory)
    private List<User> users = new ArrayList<>();

    public UserService (String ip, int port) throws IOException{

        super(ip, port);
        addContext(context, new UserHandler());


    }

    public void createUser(UserPostRequest req) {

        // create a new user with hashed password 
        // generate a UserPostResponse 
        // return response

        User newUser = new User(req.getId(), req.getUsername(), req.getEmail(), SecurityUtils.SHA256Hash(req.getPassword()));
        // append to list of users
        users.add(newUser);

        System.out.println(newUser.getPassword());
    }

    public void deleteUser() {

    }

    public void updateUser() {

    }

    class UserHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            System.out.println(method);

            switch (method) {
                case "POST":

                    InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
                    Gson gson = new Gson();
                    UserPostRequest req = gson.fromJson(reader, UserPostRequest.class);

                    switch (req.getCommand()) {
                        case "create":
                            System.out.println("Create command detected!");
                            createUser(req);
                            break;
                        case "update":
                            System.out.println("Update command detected!");
                            break;
                        case "delete":
                            System.out.println("Delete command detected!");
                            break;
                        default:
                            // unknown post request, return some kind of error
                            break;
                    }
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
