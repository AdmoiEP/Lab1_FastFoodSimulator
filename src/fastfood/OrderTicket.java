package fastfood;

import java.util.concurrent.CompletableFuture;

/**
 * One order. The two futures are the two promises from the lab:
 * the server waits while the cook prepares, and the customer waits in line.
 */
public final class OrderTicket {
    private final int number;
    private final int customerId;
    private final CompletableFuture<OrderTicket> prepared = new CompletableFuture<OrderTicket>();
    private final CompletableFuture<Integer> readyForPickup = new CompletableFuture<Integer>();

    OrderTicket(int number, int customerId) {
        this.number = number;
        this.customerId = customerId;
    }

    public int getNumber() {
        return number;
    }

    public int getCustomerId() {
        return customerId;
    }

    /** Completed by the cook when the order is prepared. */
    CompletableFuture<OrderTicket> getPrepared() {
        return prepared;
    }

    /** Completed by the server when the order number is called. */
    CompletableFuture<Integer> getReadyForPickup() {
        return readyForPickup;
    }
}
