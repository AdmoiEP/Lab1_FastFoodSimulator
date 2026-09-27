package fastfood;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Customers arrive, an order taker issues tickets, a cook prepares them,
 * and a server calls the numbers. Each role is its own loop.
 */
public final class FastFoodSimulation {
    /** How long the "order being taken" readout stays visible. */
    static final int TAKE_ORDER_MS = 350;
    /** How long a called order number stays on the pickup counter. */
    static final int ANNOUNCE_MS = 700;
    private static final int DINING_LIMIT = 12;

    private final int arrivalIntervalMs;
    private final int cookIntervalMs;
    private final int generation;
    private final SimulationListener listener;

    private final AtomicInteger nextCustomerId = new AtomicInteger(1);
    private final AtomicInteger nextOrderNumber = new AtomicInteger(1);

    private final List<Customer> orderLine = new LinkedList<Customer>();
    private final List<Customer> servingLine = new LinkedList<Customer>();
    private final List<Customer> dining = new LinkedList<Customer>();
    private final List<OrderTicket> kitchenQueue = new LinkedList<OrderTicket>();
    private final List<OrderTicket> serviceQueue = new LinkedList<OrderTicket>();
    private final List<OrderTicket> activeTickets = new ArrayList<OrderTicket>();
    private final Object stateLock = new Object();

    private volatile boolean running;
    private Integer orderBeingTaken;
    private Customer customerAtCounter;
    private OrderTicket preparing;
    private Integer pickupOrder;

    private Thread arrivalThread;
    private Thread orderTakerThread;
    private Thread cookThread;
    private Thread serverThread;

    public FastFoodSimulation(
            int arrivalIntervalMs,
            int cookIntervalMs,
            int generation,
            SimulationListener listener) {
        if (arrivalIntervalMs <= 0 || cookIntervalMs <= 0) {
            throw new IllegalArgumentException("Intervals must be positive.");
        }
        this.arrivalIntervalMs = arrivalIntervalMs;
        this.cookIntervalMs = cookIntervalMs;
        this.generation = generation;
        this.listener = listener;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        arrivalThread = startThread(new CustomerArrival(), "customer-arrival");
        orderTakerThread = startThread(new OrderTaker(), "order-taker");
        cookThread = startThread(new Cook(), "cook");
        serverThread = startThread(new Server(), "server");
    }

    public void stop() {
        running = false;
        interrupt(arrivalThread);
        interrupt(orderTakerThread);
        interrupt(cookThread);
        interrupt(serverThread);
        synchronized (stateLock) {
            stateLock.notifyAll();
        }
        join(arrivalThread);
        join(orderTakerThread);
        join(cookThread);
        join(serverThread);
        synchronized (stateLock) {
            cancelTickets();
            orderLine.clear();
            servingLine.clear();
            dining.clear();
            kitchenQueue.clear();
            serviceQueue.clear();
            activeTickets.clear();
            orderBeingTaken = null;
            customerAtCounter = null;
            preparing = null;
            pickupOrder = null;
        }
        arrivalThread = null;
        orderTakerThread = null;
        cookThread = null;
        serverThread = null;
    }

    private Thread startThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread failed, Throwable error) {
                error.printStackTrace();
            }
        });
        thread.start();
        return thread;
    }

    private static void interrupt(Thread thread) {
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void join(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void cancelTickets() {
        CancellationException stopped = new CancellationException("simulation stopped");
        for (int i = 0; i < activeTickets.size(); i++) {
            OrderTicket ticket = activeTickets.get(i);
            ticket.getPrepared().completeExceptionally(stopped);
            ticket.getReadyForPickup().completeExceptionally(stopped);
        }
    }

    /**
     * prepared: the cook completes this promise. Its callback is the only way
     * a ticket reaches the service queue, so the server waits on the cook.
     * readyForPickup: the server completes this promise when the number is called.
     * Its callback moves that customer from the serving line into the dining area.
     */
    private void wirePromises(final OrderTicket ticket, final Customer customer) {
        ticket.getPrepared().whenComplete((done, error) -> {
            if (error != null || done == null) {
                return;
            }
            synchronized (stateLock) {
                if (!running) {
                    return;
                }
                serviceQueue.add(done);
                stateLock.notifyAll();
            }
        });
        ticket.getReadyForPickup().thenAccept(orderNumber -> {
            synchronized (stateLock) {
                servingLine.remove(customer);
                customer.setPlace(Customer.Place.DINING);
                dining.add(customer);
                while (dining.size() > DINING_LIMIT) {
                    dining.remove(0);
                }
                activeTickets.remove(ticket);
            }
        });
    }

    private void publish(String message, ViewState.Zone zone) {
        ViewState state;
        synchronized (stateLock) {
            state = snapshot(message, zone);
        }
        listener.onUpdate(state);
    }

    private ViewState snapshot(String message, ViewState.Zone zone) {
        return new ViewState(
                generation,
                orderLine.size(),
                orderBeingTaken,
                customerAtCounter == null ? null : Integer.valueOf(customerAtCounter.getId()),
                preparing == null ? null : Integer.valueOf(preparing.getNumber()),
                joinTickets(kitchenQueue),
                kitchenQueue.size(),
                pickupOrder,
                servingLine.size(),
                personLabels(orderLine, false),
                ticketLabels(kitchenQueue),
                personLabels(servingLine, true),
                personLabels(dining, true),
                zone,
                message);
    }

    private static List<String> personLabels(List<Customer> customers, boolean withOrder) {
        List<String> labels = new ArrayList<String>();
        for (int i = 0; i < customers.size(); i++) {
            Customer customer = customers.get(i);
            String order = "";
            if (withOrder && customer.getOrderNumber() != null) {
                order = "#" + customer.getOrderNumber();
            }
            labels.add("C" + customer.getId() + "|" + order);
        }
        return labels;
    }

    private static List<String> ticketLabels(List<OrderTicket> tickets) {
        List<String> labels = new ArrayList<String>();
        for (int i = 0; i < tickets.size(); i++) {
            labels.add("#" + tickets.get(i).getNumber());
        }
        return labels;
    }

    private static String joinTickets(List<OrderTicket> tickets) {
        if (tickets.isEmpty()) {
            return "\u2014";
        }
        StringBuilder text = new StringBuilder();
        int limit = Math.min(tickets.size(), 12);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(tickets.get(i).getNumber());
        }
        if (tickets.size() > limit) {
            text.append(", ...");
        }
        return text.toString();
    }

    private final class CustomerArrival implements Runnable {
        @Override
        public void run() {
            try {
                while (running) {
                    Customer customer = new Customer(nextCustomerId.getAndIncrement());
                    synchronized (stateLock) {
                        if (!running) {
                            return;
                        }
                        orderLine.add(customer);
                        stateLock.notifyAll();
                    }
                    publish(
                            "Customer " + customer.getId() + " joins the order line.",
                            ViewState.Zone.ORDER_LINE);
                    Thread.sleep(arrivalIntervalMs);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private final class OrderTaker implements Runnable {
        @Override
        public void run() {
            try {
                while (running && takeNextOrder()) {
                    // The next customer is handled on the following iteration.
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean takeNextOrder() throws InterruptedException {
        Customer customer;
        int number;
        synchronized (stateLock) {
            while (running && orderLine.isEmpty()) {
                stateLock.wait(200);
            }
            if (!running || orderLine.isEmpty()) {
                return false;
            }
            customer = orderLine.remove(0);
            number = nextOrderNumber.getAndIncrement();
            customer.setOrderNumber(number);
            customer.setPlace(Customer.Place.AT_COUNTER);
            customerAtCounter = customer;
            orderBeingTaken = Integer.valueOf(number);
        }
        publish(
                "Order taker is taking order #" + number + " from customer " + customer.getId() + ".",
                ViewState.Zone.ORDER_TAKER);

        Thread.sleep(TAKE_ORDER_MS);
        if (!running) {
            return false;
        }

        OrderTicket ticket = new OrderTicket(number, customer.getId());
        wirePromises(ticket, customer);
        synchronized (stateLock) {
            if (!running) {
                return false;
            }
            customerAtCounter = null;
            orderBeingTaken = null;
            customer.setPlace(Customer.Place.SERVING_LINE);
            servingLine.add(customer);
            kitchenQueue.add(ticket);
            activeTickets.add(ticket);
            stateLock.notifyAll();
        }
        publish(
                "Order #" + number + " goes to the kitchen. Customer " + customer.getId()
                        + " waits in the serving line.",
                ViewState.Zone.KITCHEN);
        return true;
    }

    private final class Cook implements Runnable {
        @Override
        public void run() {
            try {
                while (running) {
                    cookNext();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cookNext() throws InterruptedException {
        OrderTicket ticket;
        synchronized (stateLock) {
            while (running && kitchenQueue.isEmpty()) {
                stateLock.wait(200);
            }
            if (!running || kitchenQueue.isEmpty()) {
                return;
            }
            ticket = kitchenQueue.remove(0);
            preparing = ticket;
        }
        publish("Cook is preparing order #" + ticket.getNumber() + ".", ViewState.Zone.KITCHEN);

        Thread.sleep(cookIntervalMs);
        if (!running) {
            return;
        }

        synchronized (stateLock) {
            if (preparing == ticket) {
                preparing = null;
            }
        }
        publish(
                "Cook finished order #" + ticket.getNumber() + " and placed it for the server.",
                ViewState.Zone.PICKUP);
        ticket.getPrepared().complete(ticket);
    }

    private final class Server implements Runnable {
        @Override
        public void run() {
            try {
                while (running) {
                    serveNext();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void serveNext() throws InterruptedException {
        OrderTicket ticket;
        synchronized (stateLock) {
            while (running && serviceQueue.isEmpty()) {
                stateLock.wait(200);
            }
            if (!running || serviceQueue.isEmpty()) {
                return;
            }
            ticket = serviceQueue.remove(0);
            pickupOrder = Integer.valueOf(ticket.getNumber());
        }
        publish(
                "Server calls order #" + ticket.getNumber() + ". It is ready for pickup.",
                ViewState.Zone.PICKUP);

        Thread.sleep(ANNOUNCE_MS);
        if (!running) {
            return;
        }

        ticket.getReadyForPickup().complete(ticket.getNumber());
        synchronized (stateLock) {
            if (pickupOrder != null && pickupOrder.intValue() == ticket.getNumber()) {
                pickupOrder = null;
            }
        }
        publish(
                "Customer " + ticket.getCustomerId() + " picked up order #" + ticket.getNumber()
                        + " and went to the dining area.",
                ViewState.Zone.DINING);
    }
}
