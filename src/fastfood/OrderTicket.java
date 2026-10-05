package fastfood;

import java.util.concurrent.CompletableFuture;

public final class OrderTicket {
    private final int number;

    private final int customerId;
    /** Промис «заказ готов»: его завершает повар, после этого чек видит официант. */
    private final CompletableFuture<OrderTicket> prepared = new CompletableFuture<OrderTicket>();
    /** Промис «номер назван»: её завершает официант, после этого клиент уходит в зал. */
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

    CompletableFuture<OrderTicket> getPrepared() {
        return prepared;
    }

    CompletableFuture<Integer> getReadyForPickup() {
        return readyForPickup;
    }
}
