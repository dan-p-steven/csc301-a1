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
import Shared.HttpUtils;

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

    public void createUser(HttpExchange exchange, UserPostRequest req) throws IOException {

        // create a new user with hashed password 
        // generate a UserPostResponse 
        // return response


        // all fields must be required.
        if (req.getId() == null || req.getEmail() == null || req.getUsername() == null || req.getPassword() == null ) {
            // return 400 error empty data
            HttpUtils.sendHttpResponse(exchange, 400, "{}");
        } else {
            // check if the ids are dupe
            for (User u : this.users) {
                if (u.getId() == req.getId()) {

                    // return 409 error
                    HttpUtils.sendHttpResponse(exchange, 409, "{}");
                }
            }

            // create a new user
            User newUser = new User(req.getId(), req.getUsername(), req.getEmail(), SecurityUtils.SHA256Hash(req.getPassword()));

            // add to list
            this.users.add(newUser);

            // turn user into json string
            String data = gson.toJson(newUser);

            // return 200 success and object
            HttpUtils.sendHttpResponse(exchange, 200, data);
        }
    }

    public void updateUser(HttpExchange exchange, UserPostRequest req) throws IOException {
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
                HttpUtils.sendHttpResponse(exchange, 200, data);
            }
        }

        // req not in list
        // return 400 {}
        HttpUtils.sendHttpResponse(exchange, 400, "{}");
    }

    public void deleteUser(HttpExchange exchange, UserPostRequest req) throws IOException {

        if (req.getId() == null || req.getEmail() == null || req.getUsername() == null || req.getPassword() == null ) {
            // a value was null
            HttpUtils.sendHttpResponse(exchange, 400, "{}");

        } else {
            // not empty request, need to ensure user exists
            for (User u : this.users) {
                if (u.getId() == req.getId()) {
                    // found match
                    // need to validate values
                    if (u.getEmail().equals(req.getEmail()) &&
                        u.getUsername().equals(req.getUsername()) && u.getPassword().equals(SecurityUtils.SHA256Hash(req.getPassword()))) {
                        // valid match
                        // delete u from users, return success
                        this.users.remove(u);
                        HttpUtils.sendHttpResponse(exchange, 200, "{}");

                    } else {
                        // invalid match
                        HttpUtils.sendHttpResponse(exchange, 400, "{}");

                    }
                }
            }

            // user id DNE
            HttpUtils.sendHttpResponse(exchange, 400, "{}");
        }
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
                            createUser(exchange, req);
                            break;

                        case "update":

                            System.out.println("Update command detected!");
                            updateUser(exchange, req);
                            break;

                        case "delete":

                            System.out.println("Delete command detected!");
                            deleteUser(exchange, req);
                            break;

                        default:

                            // unknown post request, return some kind of error
                            HttpUtils.sendHttpResponse(exchange, 400, "{}");
                            break;
                    }
                    break;

                case "GET":
                    getUser(exchange, exchange.getRequestURI().getPath());

                default:
                    // unknown http request method
                    HttpUtils.sendHttpResponse(exchange, 400, "{}");
                    break;
            }
        }
    }

    public void getUser(HttpExchange exchange, String path) throws IOException {

        String[] splitPath = path.split("/");
        String contextValue = context.split("/")[1];
        System.out.println(contextValue);

        if (splitPath.length != 3) {
            // fail, 400 {}
            System.out.println("url path length not 2: " + splitPath.length);
            HttpUtils.sendHttpResponse(exchange, 400, "{}");

        } else if (splitPath[0].equals("user")) {
           // fail  400 {}
           System.out.println("first url path not 'user'");
           HttpUtils.sendHttpResponse(exchange, 400, "{}");

        } else {

            // check if user id is a valid int in user database 
            try {

                // try to turn id into int
                int id = Integer.parseInt(splitPath[2]);
                System.out.println(id);

                // check if we have user with that id in db
                for (User u: this.users) {
                    if (u.getId() == id) {

                        // success, 200 and user
                        String data = gson.toJson(u);
                        HttpUtils.sendHttpResponse(exchange, 200, data);

                    }
                }

            } catch (NumberFormatException e) {

                // there are letters in id , 400 {}
                System.out.println("id not numeric");
                HttpUtils.sendHttpResponse(exchange, 400, "{}");
            }
        }

        // user not found, return 404
        HttpUtils.sendHttpResponse(exchange, 404, "{}");
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
