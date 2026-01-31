
/*
 * Represents the data structure for a "user". This is a simple data holder
 * and performs no computations.
 *
 * @author Daniel Steven
 *
 */

package UserService;

public class User {

    private int id;
    private String email;
    private String username;
    private String password;
    
    // Constructor
    public User(int id, String username, String email, String password) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.password = password;
    }
    
    // Getters
    public int getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
