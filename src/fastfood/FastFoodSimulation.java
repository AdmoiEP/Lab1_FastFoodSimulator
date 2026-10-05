package fastfood;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public final class FastFoodSimulation {

    /** Сколько касс принимают заказы одновременно. */
    public static final int CASHIER_COUNT = 2;

    /** Сколько поваров готовят одновременно. */
    public static final int COOK_COUNT = 3;

    /** Сколько миллисекунд касса оформляет один заказ. */
    static final int TAKE_ORDER_MS = 1050;
    /** Сколько миллисекунд официант держит номер на табло. */
    static final int ANNOUNCE_MS = 700;
    /** Сколько последних клиентов показывать в зале. */
    private static final int DINING_LIMIT = 12;

    /** Интервал прихода нового клиента, мс. */
    private final int arrivalIntervalMs;
    /** Время готовки одного заказа, мс. */
    private final int cookIntervalMs;
    /** Номер запуска, чтобы окно не рисовало старые события. */
    private final int generation;
    /** Куда отправлять снимок состояния для окна. */
    private final SimulationListener listener;

    /** Номер следующего клиента. */
    private final AtomicInteger nextCustomerId = new AtomicInteger(1);
    /** Номер следующего заказа. */
    private final AtomicInteger nextOrderNumber = new AtomicInteger(1);

    /** Клиенты, которые ждут, чтобы сделать заказ. */
    private final List<Customer> orderLine = new LinkedList<Customer>();
    /** Клиенты, которых кассы обслуживают прямо сейчас. */
    private final List<Customer> customersAtCounters = new ArrayList<Customer>();
    /** Клиенты, которые ждут выдачи заказа. */
    private final List<Customer> servingLine = new LinkedList<Customer>();
    /** Клиенты, которые уже забрали заказ. */
    private final List<Customer> dining = new LinkedList<Customer>();
    /** Чеки, которые ещё ждут повара. */
    private final List<OrderTicket> kitchenQueue = new LinkedList<OrderTicket>();
    /** Чеки, которые повара готовят прямо сейчас. */
    private final List<OrderTicket> preparingOrders = new ArrayList<OrderTicket>();
    /** Готовые чеки, которые ждут официанта. */
    private final List<OrderTicket> serviceQueue = new LinkedList<OrderTicket>();
    /** Заказы, которые ещё не забрали. */
    private final List<OrderTicket> activeTickets = new ArrayList<OrderTicket>();
    /** Замок на общие очереди. */
    private final Object stateLock = new Object();

    /** Идёт ли симуляция. */
    private volatile boolean running;
    /** Номер, который официант называет сейчас. */
    private Integer pickupOrder;

    /** Поток, который добавляет новых клиентов. */
    private Thread arrivalThread;
    /** Потоки касс. */
    private final List<Thread> cashierThreads = new ArrayList<Thread>();
    /** Потоки поваров. */
    private final List<Thread> cookThreads = new ArrayList<Thread>();
    /** Поток официанта. */
    private Thread serverThread;

    public FastFoodSimulation(
            int arrivalIntervalMs,
            int cookIntervalMs,
            int generation,
            SimulationListener listener) {
        if (arrivalIntervalMs <= 0 || cookIntervalMs <= 0) {
            throw new IllegalArgumentException("Intervals must be positive.");
        }
        if (CASHIER_COUNT < 1 || COOK_COUNT < 1) {
            throw new IllegalArgumentException("CASHIER_COUNT and COOK_COUNT must be at least 1.");
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
        for (int cashierId = 1; cashierId <= CASHIER_COUNT; cashierId++) {
            cashierThreads.add(startThread(new OrderTaker(cashierId), "cashier-" + cashierId));
        }
        for (int cookId = 1; cookId <= COOK_COUNT; cookId++) {
            cookThreads.add(startThread(new Cook(cookId), "cook-" + cookId));
        }
        serverThread = startThread(new Server(), "server");
    }

    public void stop() {
        running = false;
        interrupt(arrivalThread);
        interruptAll(cashierThreads);
        interruptAll(cookThreads);
        interrupt(serverThread);
        synchronized (stateLock) {
            stateLock.notifyAll();
        }
        join(arrivalThread);
        joinAll(cashierThreads);
        joinAll(cookThreads);
        join(serverThread);
        synchronized (stateLock) {
            cancelTickets();
            orderLine.clear();
            customersAtCounters.clear();
            servingLine.clear();
            dining.clear();
            kitchenQueue.clear();
            preparingOrders.clear();
            serviceQueue.clear();
            activeTickets.clear();
            pickupOrder = null;
        }
        arrivalThread = null;
        cashierThreads.clear();
        cookThreads.clear();
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

    private static void interruptAll(List<Thread> threads) {
        for (int i = 0; i < threads.size(); i++) {
            interrupt(threads.get(i));
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

    private static void joinAll(List<Thread> threads) {
        for (int i = 0; i < threads.size(); i++) {
            join(threads.get(i));
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
                joinCustomerIds(customersAtCounters),
                joinCustomerOrders(customersAtCounters),
                joinTickets(preparingOrders),
                joinTickets(kitchenQueue),
                kitchenQueue.size(),
                pickupOrder,
                servingLine.size(),
                personLabels(orderLine, false),
                counterLabels(customersAtCounters),
                ticketLabels(preparingOrders),
                ticketLabels(kitchenQueue),
                personLabels(servingLine, true),
                personLabels(dining, true),
                zone,
                message);
    }

    private static List<String> counterLabels(List<Customer> customers) {
        List<String> labels = new ArrayList<String>();
        for (int i = 0; i < customers.size(); i++) {
            Customer customer = customers.get(i);
            String order = customer.getOrderNumber() == null ? "" : "#" + customer.getOrderNumber();
            labels.add("C" + customer.getId() + "|" + order);
        }
        return labels;
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

    private static String joinCustomerIds(List<Customer> customers) {
        if (customers.isEmpty()) {
            return "\u2014";
        }
        StringBuilder text = new StringBuilder();
        int limit = Math.min(customers.size(), 12);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append('C').append(customers.get(i).getId());
        }
        if (customers.size() > limit) {
            text.append(", ...");
        }
        return text.toString();
    }

    private static String joinCustomerOrders(List<Customer> customers) {
        if (customers.isEmpty()) {
            return "\u2014";
        }
        StringBuilder text = new StringBuilder();
        int limit = Math.min(customers.size(), 12);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                text.append(", ");
            }
            Integer orderNumber = customers.get(i).getOrderNumber();
            text.append(orderNumber == null ? "?" : orderNumber.toString());
        }
        if (customers.size() > limit) {
            text.append(", ...");
        }
        return text.toString();
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
        private final int cashierId;

        private OrderTaker(int cashierId) {
            this.cashierId = cashierId;
        }

        @Override
        public void run() {
            try {
                while (running && takeNextOrder(cashierId)) {
                    // The next customer is handled on the following iteration.
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean takeNextOrder(int cashierId) throws InterruptedException {
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
            customersAtCounters.add(customer);
        }
        publish(
                "Cashier " + cashierId + " is taking order #" + number
                        + " from customer " + customer.getId() + ".",
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
            customersAtCounters.remove(customer);
            customer.setPlace(Customer.Place.SERVING_LINE);
            servingLine.add(customer);
            kitchenQueue.add(ticket);
            activeTickets.add(ticket);
            stateLock.notifyAll();
        }
        publish(
                "Cashier " + cashierId + " sent order #" + number
                        + " to the kitchen. Customer " + customer.getId()
                        + " waits in the serving line.",
                ViewState.Zone.KITCHEN);
        return true;
    }

    private final class Cook implements Runnable {
        private final int cookId;

        private Cook(int cookId) {
            this.cookId = cookId;
        }

        @Override
        public void run() {
            try {
                while (running) {
                    cookNext(cookId);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cookNext(int cookId) throws InterruptedException {
        OrderTicket ticket;
        synchronized (stateLock) {
            while (running && kitchenQueue.isEmpty()) {
                stateLock.wait(200);
            }
            if (!running || kitchenQueue.isEmpty()) {
                return;
            }
            ticket = kitchenQueue.remove(0);
            preparingOrders.add(ticket);
        }
        publish(
                "Cook " + cookId + " is preparing order #" + ticket.getNumber() + ".",
                ViewState.Zone.KITCHEN);

        Thread.sleep(cookIntervalMs);
        if (!running) {
            return;
        }

        synchronized (stateLock) {
            preparingOrders.remove(ticket);
        }
        publish(
                "Cook " + cookId + " finished order #" + ticket.getNumber()
                        + " and placed it for the server.",
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
