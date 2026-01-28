package ProductService;

public class ProductPostRequest {

    private String command;
    private Integer id;
    private String name;
    private String description;
    private float price;
    private Integer quantity;

    public ProductPostRequest(String command, int id, String name, String description, float price, int quantity) {
        this.command = command;
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;

    }

    // Getters
    public String getCommand() {
        return command;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public float getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    // Setters
    public void setCommand(String command) {
        this.command = command;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(float price) {
        this.price = price;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}


