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
import UserService.UserPostResponse;

import java.util.ArrayList;
import java.util.List;


public class UserService extends MicroService{

    // API endpoint
    private String context = "/user";

    // "database" (temp memory)
    private List<User> users = new ArrayList<>();

    private Gson gson = new Gson();

    public UserService (String ip, int port) throws IOException{

        super(ip, port);
        addContext(context, new UserHandler());


    }

    public UserPostResponse createUser(UserPostRequest req) {

        // create a new user with hashed password 
        // generate a UserPostResponse 
        // return response


        // all fields must be required.
        if (req.getId() == 0 || req.getEmail() == null || req.getUsername() == null || req.getPassword() == null ) {
            // return 400 error empty data
            return new UserPostResponse(400, "{}");
        } else {
            // check if the ids are dupe
            for (User u : this.users) {
                if (u.getId() == req.getId()) {

                    // return 409 error
                    return new UserPostResponse(409, "{}");
                }
            }

            // create a new user
            User newUser = new User(req.getId(), req.getUsername(), req.getEmail(), SecurityUtils.SHA256Hash(req.getPassword()));

            // add to list
            this.users.add(newUser);

            // turn user into json string
            String data = gson.toJson(newUser);

            // return 200 success and object
            return new UserPostResponse(200, data);
        }
    }

    public UserPostResponse updateUser(UserPostRequest req) {
        for (User u : this.users) {

            if (u.getId() == req.getId()) {

                // update fields
                if (req.getUsername() != null) {
                    u.setUsername(req.getUsername());
                }

                if (req.getEmail() != null) {
                    u.setEmail(req.getEmail());
                }

                if (req.getPassword() != null) {
                    u.setPassword(SecurityUtils.SHA256Hash(req.getPassword()));
                }

                // success
                String data = gson.toJson(u);
                return new UserPostResponse(200, data);
            }
        }

        // req not in list
        // return 400 {}
        return new UserPostResponse(400, "{}");
    }

    public void deleteUser() {

    }


    class UserHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();
            UserPostResponse resp;
            System.out.println(method);

            switch (method) {
                case "POST":

                    InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
                    UserPostRequest req = gson.fromJson(reader, UserPostRequest.class);

                    switch (req.getCommand()) {
                        case "create":
                            System.out.println("Create command detected!");
                            resp = createUser(req);

                            System.out.println(resp.getStatus());
                            System.out.println(resp.getHeaders());
                            System.out.println(resp.getData());

                            break;

                        case "update":
                            System.out.println("Update command detected!");
                            System.out.println(req.getEmail());
                            resp = updateUser(req);

                            System.out.println(resp.getStatus());
                            System.out.println(resp.getHeaders());
                            System.out.println(resp.getData());
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
