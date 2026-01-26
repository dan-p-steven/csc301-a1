package UserService;

public class UserPostResponse {
    private int id;
    private String username;
    private String email;
    private String password;

    public UserPostResponse(int id, String username, String email, String password) {
        this.id = id;
        this.username = username;
        this.email = email;

        this.password = password; //need to hash this with 256
    }
}
