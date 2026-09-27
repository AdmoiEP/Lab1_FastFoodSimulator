package fastfood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable picture of the restaurant at one moment, safe to draw on screen.
 */
public final class ViewState {
    public enum Zone {
        NONE,
        ORDER_LINE,
        ORDER_TAKER,
        KITCHEN,
        PICKUP,
        DINING
    }

    private final int generation;
    private final int customersWaitingToOrder;
    private final Integer orderBeingTaken;
    private final Integer customerAtCounter;
    private final Integer orderBeingPrepared;
    private final String waitingOrdersText;
    private final int waitingOrderCount;
    private final Integer orderReadyForPickup;
    private final int customersInServingLine;
    private final List<String> orderLineLabels;
    private final List<String> kitchenTicketLabels;
    private final List<String> servingLineLabels;
    private final List<String> diningLabels;
    private final Zone highlight;
    private final String message;

    public ViewState(
            int generation,
            int customersWaitingToOrder,
            Integer orderBeingTaken,
            Integer customerAtCounter,
            Integer orderBeingPrepared,
            String waitingOrdersText,
            int waitingOrderCount,
            Integer orderReadyForPickup,
            int customersInServingLine,
            List<String> orderLineLabels,
            List<String> kitchenTicketLabels,
            List<String> servingLineLabels,
            List<String> diningLabels,
            Zone highlight,
            String message) {
        this.generation = generation;
        this.customersWaitingToOrder = customersWaitingToOrder;
        this.orderBeingTaken = orderBeingTaken;
        this.customerAtCounter = customerAtCounter;
        this.orderBeingPrepared = orderBeingPrepared;
        this.waitingOrdersText = waitingOrdersText;
        this.waitingOrderCount = waitingOrderCount;
        this.orderReadyForPickup = orderReadyForPickup;
        this.customersInServingLine = customersInServingLine;
        this.orderLineLabels = freeze(orderLineLabels);
        this.kitchenTicketLabels = freeze(kitchenTicketLabels);
        this.servingLineLabels = freeze(servingLineLabels);
        this.diningLabels = freeze(diningLabels);
        this.highlight = highlight;
        this.message = message;
    }

    public static ViewState cleared(int generation, String message) {
        List<String> none = Collections.emptyList();
        return new ViewState(
                generation,
                0,
                null,
                null,
                null,
                "\u2014",
                0,
                null,
                0,
                none,
                none,
                none,
                none,
                Zone.NONE,
                message);
    }

    private static List<String> freeze(List<String> labels) {
        return Collections.unmodifiableList(new ArrayList<String>(labels));
    }

    public int getGeneration() {
        return generation;
    }

    public int getCustomersWaitingToOrder() {
        return customersWaitingToOrder;
    }

    public Integer getOrderBeingTaken() {
        return orderBeingTaken;
    }

    public Integer getCustomerAtCounter() {
        return customerAtCounter;
    }

    public Integer getOrderBeingPrepared() {
        return orderBeingPrepared;
    }

    public String getWaitingOrdersText() {
        return waitingOrdersText;
    }

    public int getWaitingOrderCount() {
        return waitingOrderCount;
    }

    public Integer getOrderReadyForPickup() {
        return orderReadyForPickup;
    }

    public int getCustomersInServingLine() {
        return customersInServingLine;
    }

    public List<String> getOrderLineLabels() {
        return orderLineLabels;
    }

    public List<String> getKitchenTicketLabels() {
        return kitchenTicketLabels;
    }

    public List<String> getServingLineLabels() {
        return servingLineLabels;
    }

    public List<String> getDiningLabels() {
        return diningLabels;
    }

    public Zone getHighlight() {
        return highlight;
    }

    public String getMessage() {
        return message;
    }
}
