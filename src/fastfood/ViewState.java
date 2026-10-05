package fastfood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final String customersAtCountersText;
    private final String ordersBeingTakenText;
    private final String ordersBeingPreparedText;
    private final String waitingOrdersText;
    private final int waitingOrderCount;
    private final Integer orderReadyForPickup;
    private final int customersInServingLine;
    private final List<String> orderLineLabels;
    private final List<String> counterLabels;
    private final List<String> preparingLabels;
    private final List<String> kitchenTicketLabels;
    private final List<String> servingLineLabels;
    private final List<String> diningLabels;
    private final Zone highlight;
    private final String message;

    public ViewState(
            int generation,
            int customersWaitingToOrder,
            String customersAtCountersText,
            String ordersBeingTakenText,
            String ordersBeingPreparedText,
            String waitingOrdersText,
            int waitingOrderCount,
            Integer orderReadyForPickup,
            int customersInServingLine,
            List<String> orderLineLabels,
            List<String> counterLabels,
            List<String> preparingLabels,
            List<String> kitchenTicketLabels,
            List<String> servingLineLabels,
            List<String> diningLabels,
            Zone highlight,
            String message) {
        this.generation = generation;
        this.customersWaitingToOrder = customersWaitingToOrder;
        this.customersAtCountersText = customersAtCountersText;
        this.ordersBeingTakenText = ordersBeingTakenText;
        this.ordersBeingPreparedText = ordersBeingPreparedText;
        this.waitingOrdersText = waitingOrdersText;
        this.waitingOrderCount = waitingOrderCount;
        this.orderReadyForPickup = orderReadyForPickup;
        this.customersInServingLine = customersInServingLine;
        this.orderLineLabels = freeze(orderLineLabels);
        this.counterLabels = freeze(counterLabels);
        this.preparingLabels = freeze(preparingLabels);
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
                "\u2014",
                "\u2014",
                "\u2014",
                "\u2014",
                0,
                null,
                0,
                none,
                none,
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

    public String getCustomersAtCountersText() {
        return customersAtCountersText;
    }

    public String getOrdersBeingTakenText() {
        return ordersBeingTakenText;
    }

    public String getOrdersBeingPreparedText() {
        return ordersBeingPreparedText;
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

    public List<String> getCounterLabels() {
        return counterLabels;
    }

    public List<String> getPreparingLabels() {
        return preparingLabels;
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
