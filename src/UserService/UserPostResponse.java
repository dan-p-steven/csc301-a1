package UserService;

import java.util.Map;
import java.util.HashMap;

public class UserPostResponse {

    private int status;
    private Map<String, String> headers;
    private String data;

    public UserPostResponse() {
        // empty constructor
        this.headers = new HashMap<>();
        this.headers.put("Content-Type", "application/json");

    }

    public UserPostResponse(int status, String data) {
        this.status = status;

        this.headers = new HashMap<>();
        this.headers.put("Content-Type", "application/json");

        this.data = data;
    }

    // Getters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
    public String getData() {
        return data;
    }

    // Setters
    public void setData(String data) {
        this.data = data;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    // Add extra headers if needed
    public void addHeaders(String key, String val) {
        this.headers.put(key, val);
    }
}
