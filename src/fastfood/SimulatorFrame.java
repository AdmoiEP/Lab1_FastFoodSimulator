package fastfood;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;

public class SimulatorFrame extends JFrame implements SimulationListener {
    private static final int MIN_INTERVAL_MS = 100;
    private static final int MAX_INTERVAL_MS = 120_000;
    private static final String DASH = "\u2014";
    private static final Color PAPER = new Color(244, 241, 234);
    private static final Color WARNING = new Color(164, 28, 36);
    private static final Color OK = new Color(20, 110, 50);

    private final JTextField arrivalField = new JTextField("1000", 8);
    private final JTextField cookField = new JTextField("2000", 8);
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JLabel messageLabel = new JLabel(" ");

    private final JTextField customersWaitingField = readout(true);
    private final JTextField customerAtCounterField = readout(true);
    private final JTextField orderBeingTakenField = readout(true);
    private final JTextField preparingField = readout(true);
    private final JTextField waitingOrdersField = readout(false);
    private final JTextField waitingCountField = readout(true);
    private final JTextField pickupField = readout(true);
    private final JTextField servingCountField = readout(true);

    private final RestaurantView restaurantView = new RestaurantView();
    private final JTextArea logArea = new JTextArea(7, 40);

    private FastFoodSimulation simulation;
    private int generation;

    public SimulatorFrame() {
        super("Fast Food Simulator");
        customersWaitingField.setText("0");
        waitingCountField.setText("0");
        servingCountField.setText("0");
        styleList(customerAtCounterField);
        styleList(orderBeingTakenField);
        styleList(preparingField);
        styleList(waitingOrdersField);

        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        styleInput(arrivalField);
        styleInput(cookField);
        messageLabel.setFont(messageLabel.getFont().deriveFont(13f));
        stopButton.setEnabled(false);
        Dimension buttonSize = new Dimension(96, 32);
        startButton.setPreferredSize(buttonSize);
        stopButton.setPreferredSize(buttonSize);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.setBackground(PAPER);
        root.add(buildControls(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        setContentPane(root);

        startButton.addActionListener(event -> onStart());
        stopButton.addActionListener(event -> onStop());
        getRootPane().setDefaultButton(startButton);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                onStop();
                dispose();
                System.exit(0);
            }
        });
        pack();
        setMinimumSize(new Dimension(980, 700));
        setLocationRelativeTo(null);
    }

    @Override
    public void onUpdate(ViewState state) {
        SwingUtilities.invokeLater(() -> apply(state));
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 6, 2, 6);
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;

        constraints.gridx = 0;
        panel.add(new JLabel("Customer arrival interval (ms):"), constraints);
        constraints.gridx = 1;
        panel.add(arrivalField, constraints);
        constraints.gridx = 2;
        panel.add(new JLabel("Order fulfilment interval (ms):"), constraints);
        constraints.gridx = 3;
        panel.add(cookField, constraints);
        constraints.gridx = 4;
        panel.add(startButton, constraints);
        constraints.gridx = 5;
        panel.add(stopButton, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 6;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(messageLabel, constraints);
        return panel;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(buildStats(), BorderLayout.NORTH);

        JLabel legend = new JLabel(
                "<html><div style='width:940px'>Circles are customers, tickets are orders. "
                        + "Each order has two promises: prepared (cook to server) "
                        + "and ready for pickup (server to customer).</div></html>");
        legend.setFont(legend.getFont().deriveFont(12f));
        JPanel diagram = new JPanel(new BorderLayout(0, 4));
        diagram.setOpaque(false);
        diagram.add(legend, BorderLayout.NORTH);
        diagram.add(restaurantView, BorderLayout.CENTER);
        center.add(diagram, BorderLayout.CENTER);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Activity"));
        logScroll.setPreferredSize(new Dimension(800, 150));
        center.add(logScroll, BorderLayout.SOUTH);
        return center;
    }

    private JPanel buildStats() {
        JPanel grid = new JPanel(new GridLayout(1, 4, 8, 0));
        grid.setOpaque(false);
        grid.add(card(
                "Order line",
                labeled("Customers waiting to order", customersWaitingField)));
        grid.add(card(
                "Order takers",
                labeled("Customers at the counters", customerAtCounterField),
                labeled("Orders being taken", orderBeingTakenField)));
        grid.add(card(
                "Kitchen",
                labeled("Orders being prepared", preparingField),
                labeled("Waiting orders (next first)", waitingOrdersField),
                labeled("Waiting count", waitingCountField)));
        grid.add(card(
                "Pickup",
                labeled("Order ready for pickup", pickupField),
                labeled("Customers in the serving line", servingCountField)));
        return grid;
    }

    private JPanel card(String title, JComponent... rows) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                new EmptyBorder(4, 8, 8, 8)));
        for (int i = 0; i < rows.length; i++) {
            rows[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(rows[i]);
            if (i < rows.length - 1) {
                panel.add(Box.createVerticalStrut(6));
            }
        }
        return panel;
    }

    private JPanel labeled(String caption, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.add(new JLabel(caption), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        return panel;
    }

    private static void styleList(JTextField field) {
        field.setHorizontalAlignment(JTextField.LEADING);
        field.setFont(new Font("Consolas", Font.PLAIN, 15));
    }

    private static void styleInput(JTextField field) {
        field.setFont(new Font("Consolas", Font.PLAIN, 18));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setPreferredSize(new Dimension(120, 34));
        field.setMinimumSize(new Dimension(120, 34));
        field.setDisabledTextColor(new Color(30, 30, 30));
    }

    private static JTextField readout(boolean large) {
        JTextField field = new JTextField(DASH);
        field.setEditable(false);
        field.setFocusable(false);
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setFont(new Font("Consolas", large ? Font.BOLD : Font.PLAIN, large ? 20 : 16));
        return field;
    }

    private void onStart() {
        if (simulation != null) {
            return;
        }
        String arrivalText = arrivalField.getText().trim();
        String cookText = cookField.getText().trim();
        String arrivalError = validateInterval(arrivalText, "customer arrival", "1000");
        String cookError = validateInterval(cookText, "order fulfilment", "2000");
        if (arrivalError != null || cookError != null) {
            StringBuilder message = new StringBuilder();
            if (arrivalError != null) {
                message.append(arrivalError);
            }
            if (cookError != null) {
                if (message.length() > 0) {
                    message.append(' ');
                }
                message.append(cookError);
            }
            showMessage(message.toString(), WARNING);
            return;
        }

        int arrival = Integer.parseInt(arrivalText);
        int cook = Integer.parseInt(cookText);
        generation++;
        logArea.setText("");
        showMessage(
                "Simulation is running. Cashiers: " + FastFoodSimulation.CASHIER_COUNT
                        + ". Cooks: " + FastFoodSimulation.COOK_COUNT
                        + ". A new customer arrives every " + arrival
                        + " ms. Each cook fulfils one order every " + cook + " ms.",
                OK);
        setRunningUi(true);
        simulation = new FastFoodSimulation(arrival, cook, generation, this);
        simulation.start();
    }

    private void onStop() {
        if (simulation == null) {
            return;
        }
        generation++;
        FastFoodSimulation stopping = simulation;
        simulation = null;
        stopping.stop();
        apply(ViewState.cleared(generation, "Simulation stopped."));
        showMessage("Simulation stopped.", OK);
        setRunningUi(false);
    }

    private void apply(ViewState state) {
        if (state.getGeneration() != generation) {
            return;
        }
        customersWaitingField.setText(Integer.toString(state.getCustomersWaitingToOrder()));
        customerAtCounterField.setText(state.getCustomersAtCountersText());
        customerAtCounterField.setToolTipText(state.getCustomersAtCountersText());
        orderBeingTakenField.setText(state.getOrdersBeingTakenText());
        orderBeingTakenField.setToolTipText(state.getOrdersBeingTakenText());
        preparingField.setText(state.getOrdersBeingPreparedText());
        preparingField.setToolTipText(state.getOrdersBeingPreparedText());
        waitingOrdersField.setText(state.getWaitingOrdersText());
        waitingOrdersField.setToolTipText(state.getWaitingOrdersText());
        waitingCountField.setText(Integer.toString(state.getWaitingOrderCount()));
        pickupField.setText(dash(state.getOrderReadyForPickup()));
        servingCountField.setText(Integer.toString(state.getCustomersInServingLine()));
        restaurantView.setState(state);
        if (state.getMessage() != null && !state.getMessage().isEmpty()) {
            appendLog(state.getMessage());
        }
    }

    private void appendLog(String message) {
        logArea.append(message);
        logArea.append("\n");
        if (logArea.getDocument().getLength() > 30_000) {
            try {
                logArea.getDocument().remove(0, 10_000);
            } catch (BadLocationException ex) {
                logArea.setText(message + "\n");
            }
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void setRunningUi(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        arrivalField.setEnabled(!running);
        cookField.setEnabled(!running);
        getRootPane().setDefaultButton(running ? stopButton : startButton);
    }

    private void showMessage(String message, Color color) {
        messageLabel.setForeground(color);
        messageLabel.setText("<html><div style='width:860px'>" + message + "</div></html>");
    }

    private static String validateInterval(String text, String name, String example) {
        if (text.isEmpty()) {
            return "The " + name + " interval is empty. Enter a whole number of milliseconds, for example "
                    + example + ".";
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return "The " + name + " interval must be a whole number of milliseconds, for example " + example + ".";
        }
        if (value <= 0) {
            return "The " + name + " interval must be greater than zero.";
        }
        if (value < MIN_INTERVAL_MS) {
            return "The " + name + " interval is too small. Use at least " + MIN_INTERVAL_MS
                    + " milliseconds so the movement stays visible.";
        }
        if (value > MAX_INTERVAL_MS) {
            return "The " + name + " interval is too large. Use at most " + MAX_INTERVAL_MS + " milliseconds.";
        }
        return null;
    }

    private static String dash(Integer value) {
        return value == null ? DASH : Integer.toString(value);
    }
}
