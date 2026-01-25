package UserService;

import Shared.MicroService;


public class UserService extends MicroService{

    void updateUser(User user, int id, String email, String username, String password) {
        // Update  user with the new fields
        user.setId(id);
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(password);
    }


}
