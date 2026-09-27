package fastfood;

/**
 * A simulated customer moving through the restaurant.
 */
public final class Customer {
    public enum Place {
        ORDER_LINE,
        AT_COUNTER,
        SERVING_LINE,
        DINING
    }

    private final int id;
    private Integer orderNumber;
    private Place place;

    Customer(int id) {
        this.id = id;
        this.place = Place.ORDER_LINE;
    }

    public int getId() {
        return id;
    }

    public Integer getOrderNumber() {
        return orderNumber;
    }

    public Place getPlace() {
        return place;
    }

    void setOrderNumber(int orderNumber) {
        this.orderNumber = orderNumber;
    }

    void setPlace(Place place) {
        this.place = place;
    }
}
