/*
 * A class representing the data structure of JSON requests sent to the UserService.
 * This is a simple data holder class that does no computations.
 *
 * @author Daniel Steven
 */

package UserService;

public class UserPostRequest {

    private String command;
    private Integer id;
    private String email;
    private String username;
    private String password;

    public UserPostRequest(String command, int id, String username, String email, String password) {
        this.command = command;
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;

    }

    // Getters
    public String getCommand() {
        return command;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    // Setters
    public void setCommand(String command) {
        this.command = command;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEmail(String email) {
        this.email = email;
    }

}
