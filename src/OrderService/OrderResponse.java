public class OrderResponse {

    private Integer id;
    private Integer product_id;
    private Integer user_id;
    private Integer quantity;
    private String state;

    public OrderResponse(Integer id, Integer product_id, Integer user_id, Integer quantity, String state) {
        this.id = id;
        this.product_id = product_id;
        this.user_id = user_id;
        this.quantity = quantity;
        this.state = state;

    }

    // Getters
    public Integer getId() {
        return id;
    }

    public Integer getProductId() {
        return product_id;
    }

    public Integer getUserId() {
        return user_id;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getState() {
        return state;
    }

    // Setters
    public void setId(Integer id) {
        this.id = id;
    }

    public void setProductId(Integer productId) {
        this.product_id = productId;
    }

    public void setUserId(Integer userId) {
        this.user_id = userId;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public void setState(String state) {
        this.state = state;
    }
}
