/*
 * Represents the data structure for an "order". This is a simple data holder
 * and performs no computations.
 *
 * @author Daniel Steven
 *
 */

package OrderService;

public class Order {
    private Integer product_id;
    private Integer user_id;
    private Integer quantity;

    public Order(Integer product_id, Integer user_id, Integer quantity) {
        this.product_id = product_id;
        this.user_id = user_id;
        this.quantity = quantity;
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


    // Setters
    public void setProductId(Integer productId) {
        this.product_id = productId;
    }

    public void setUserId(Integer userId) {
        this.user_id = userId;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
