package fastfood;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Draws customers as circles and orders as tickets moving through five stations.
 */
public final class RestaurantView extends JPanel {
    private static final Color PAPER = new Color(244, 241, 234);
    private static final Color CARD = new Color(255, 252, 247);
    private static final Color HOT = new Color(255, 244, 232);
    private static final Color INK = new Color(42, 38, 34);
    private static final Color MUTED = new Color(120, 112, 102);
    private static final Color LINE = new Color(47, 111, 237);
    private static final Color COUNTER = new Color(124, 92, 191);
    private static final Color STOVE = new Color(192, 84, 40);
    private static final Color TICKET = new Color(214, 140, 70);
    private static final Color SERVING = new Color(196, 138, 28);
    private static final Color DINING = new Color(36, 140, 82);

    private ViewState state = ViewState.cleared(0, null);

    public RestaurantView() {
        setOpaque(true);
        setBackground(PAPER);
        setFont(new Font("Segoe UI", Font.PLAIN, 12));
        setPreferredSize(new Dimension(980, 250));
        setMinimumSize(new Dimension(720, 210));
    }

    public void setState(ViewState state) {
        this.state = state == null ? ViewState.cleared(0, null) : state;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(PAPER);
        g2.fillRect(0, 0, getWidth(), getHeight());

        int margin = 8;
        int gap = 22;
        int colW = Math.max(40, (getWidth() - margin * 2 - gap * 4) / 5);
        int top = 6;
        int boxH = Math.max(40, getHeight() - 12);
        String[] titles = {"Order line", "Order taker", "Kitchen", "Pickup", "Dining"};
        ViewState.Zone[] zones = {
            ViewState.Zone.ORDER_LINE,
            ViewState.Zone.ORDER_TAKER,
            ViewState.Zone.KITCHEN,
            ViewState.Zone.PICKUP,
            ViewState.Zone.DINING
        };
        for (int i = 0; i < titles.length; i++) {
            int x = margin + i * (colW + gap);
            boolean hot = state.getHighlight() == zones[i];
            Graphics2D column = (Graphics2D) g2.create();
            column.translate(x, top);
            column.setClip(0, 0, colW, boxH);
            paintColumn(column, i, hot, titles[i], subtitle(i), colW, boxH);
            column.dispose();
            if (i < titles.length - 1) {
                drawArrow(g2, x + colW, top + boxH / 2, gap);
            }
        }
        g2.dispose();
    }

    private String subtitle(int index) {
        switch (index) {
            case 0:
                return state.getCustomersWaitingToOrder() + " waiting";
            case 1:
                return state.getOrderBeingTaken() == null
                        ? "idle"
                        : "order #" + state.getOrderBeingTaken();
            case 2:
                if (state.getOrderBeingPrepared() != null) {
                    return "cooking #" + state.getOrderBeingPrepared();
                }
                return state.getWaitingOrderCount() + " waiting";
            case 3:
                if (state.getOrderReadyForPickup() != null) {
                    return "calling #" + state.getOrderReadyForPickup();
                }
                return state.getCustomersInServingLine() + " in line";
            default:
                return state.getDiningLabels().size() + " seated";
        }
    }

    private void paintColumn(
            Graphics2D g2,
            int index,
            boolean hot,
            String title,
            String subtitle,
            int width,
            int height) {
        g2.setColor(hot ? HOT : CARD);
        g2.fillRoundRect(1, 1, width - 3, height - 3, 16, 16);
        g2.setColor(hot ? STOVE : new Color(186, 178, 166));
        g2.setStroke(new BasicStroke(hot ? 2.4f : 1.2f));
        g2.drawRoundRect(1, 1, width - 3, height - 3, 16, 16);
        g2.setStroke(new BasicStroke(1f));
        g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
        g2.setColor(INK);
        g2.drawString(title, 10, 22);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        g2.setColor(MUTED);
        g2.drawString(subtitle, 10, 40);

        switch (index) {
            case 0:
                drawPeopleOrEmpty(g2, state.getOrderLineLabels(), 8, 52, width - 16, height - 60, LINE, "No one in line");
                break;
            case 1:
                drawCounter(g2, width, height);
                break;
            case 2:
                drawKitchen(g2, width, height);
                break;
            case 3:
                drawPickup(g2, width, height);
                break;
            default:
                drawPeopleOrEmpty(g2, state.getDiningLabels(), 8, 52, width - 16, height - 60, DINING, "Empty");
                break;
        }
    }

    private void drawCounter(Graphics2D g2, int width, int height) {
        if (state.getCustomerAtCounter() == null) {
            drawMuted(g2, "Waiting for a customer", 10, 78);
            return;
        }
        drawPerson(g2, "C" + state.getCustomerAtCounter() + "|", 12, 56, COUNTER);
        if (state.getOrderBeingTaken() != null) {
            drawTicket(g2, "#" + state.getOrderBeingTaken(), 68, 64, 70, 30, COUNTER);
        }
        if (height > 140) {
            drawMuted(g2, "Writing the receipt", 10, 130);
        }
    }

    private void drawKitchen(Graphics2D g2, int width, int height) {
        if (state.getOrderBeingPrepared() == null) {
            drawMuted(g2, "Nothing on the stove", 10, 70);
        } else {
            drawTicket(g2, "Cook #" + state.getOrderBeingPrepared(), 10, 52, Math.min(120, width - 20), 30, STOVE);
        }
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        g2.setColor(MUTED);
        g2.drawString("Queue (next first)", 10, 104);
        drawTickets(g2, state.getKitchenTicketLabels(), 10, 112, width - 20, height - 122, TICKET);
    }

    private void drawPickup(Graphics2D g2, int width, int height) {
        if (state.getOrderReadyForPickup() == null) {
            if (state.getServingLineLabels().isEmpty()) {
                drawMuted(g2, "Counter is empty", 10, 70);
            }
        } else {
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            g2.setColor(new Color(122, 84, 16));
            g2.drawString("#" + state.getOrderReadyForPickup(), 12, 72);
        }
        drawPeople(g2, state.getServingLineLabels(), 6, 84, width - 12, height - 96, SERVING);
    }

    private void drawPeopleOrEmpty(
            Graphics2D g2,
            java.util.List<String> labels,
            int x,
            int y,
            int width,
            int height,
            Color color,
            String emptyText) {
        if (labels.isEmpty()) {
            drawMuted(g2, emptyText, x + 2, y + 22);
            return;
        }
        drawPeople(g2, labels, x, y, width, height, color);
    }

    private void drawPeople(Graphics2D g2, java.util.List<String> labels, int x, int y, int width, int height, Color color) {
        int cellW = 48;
        int cellH = 54;
        int cols = Math.max(1, width / cellW);
        int rows = Math.max(1, height / cellH);
        int capacity = cols * rows;
        int extra = 0;
        int shown = labels.size();
        if (shown > capacity) {
            shown = Math.max(0, capacity - 1);
            extra = labels.size() - shown;
        }
        for (int i = 0; i < shown; i++) {
            int cx = x + (i % cols) * cellW;
            int cy = y + (i / cols) * cellH;
            drawPerson(g2, labels.get(i), cx, cy, color);
        }
        if (extra > 0) {
            int cx = x + (shown % cols) * cellW;
            int cy = y + (shown / cols) * cellH;
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.setColor(INK);
            g2.drawString("+" + extra, cx + 8, cy + 24);
        }
    }

    private void drawPerson(Graphics2D g2, String label, int x, int y, Color color) {
        String name = label;
        String order = "";
        int bar = label.indexOf('|');
        if (bar >= 0) {
            name = label.substring(0, bar);
            order = label.substring(bar + 1);
        }
        g2.setColor(color);
        g2.fillOval(x + 6, y + 2, 34, 34);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
        drawCentered(g2, name, x + 6, y + 2, 34, 34);
        if (!order.isEmpty()) {
            g2.setColor(INK);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            drawCentered(g2, order, x, y + 36, 46, 14);
        }
    }

    private void drawTickets(Graphics2D g2, java.util.List<String> labels, int x, int y, int width, int height, Color color) {
        if (labels.isEmpty() || height < 20) {
            return;
        }
        int chipW = 52;
        int chipH = 26;
        int gap = 6;
        int cols = Math.max(1, (width + gap) / (chipW + gap));
        int rows = Math.max(1, (height + gap) / (chipH + gap));
        int capacity = cols * rows;
        int shown = labels.size();
        int extra = 0;
        if (shown > capacity) {
            shown = Math.max(0, capacity - 1);
            extra = labels.size() - shown;
        }
        for (int i = 0; i < shown; i++) {
            int cx = x + (i % cols) * (chipW + gap);
            int cy = y + (i / cols) * (chipH + gap);
            drawTicket(g2, labels.get(i), cx, cy, chipW, chipH, color);
        }
        if (extra > 0) {
            int cx = x + (shown % cols) * (chipW + gap);
            int cy = y + (shown / cols) * (chipH + gap);
            g2.setColor(INK);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            g2.drawString("+" + extra, cx, cy + 16);
        }
    }

    private void drawTicket(Graphics2D g2, String text, int x, int y, int width, int height, Color color) {
        g2.setColor(color);
        g2.fillRoundRect(x, y, width, height, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        drawCentered(g2, text, x, y, width, height);
    }

    private void drawMuted(Graphics2D g2, String text, int x, int y) {
        g2.setColor(MUTED);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g2.drawString(text, x, y);
    }

    private void drawCentered(Graphics2D g2, String text, int x, int y, int width, int height) {
        FontMetrics metrics = g2.getFontMetrics();
        int tx = x + Math.max(0, (width - metrics.stringWidth(text)) / 2);
        int ty = y + (height - metrics.getHeight()) / 2 + metrics.getAscent();
        g2.drawString(text, tx, ty);
    }

    private void drawArrow(Graphics2D g2, int x, int midY, int gap) {
        int x1 = x + 4;
        int x2 = x + gap - 6;
        g2.setColor(new Color(110, 102, 92));
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawLine(x1, midY, x2 - 2, midY);
        Polygon head = new Polygon();
        head.addPoint(x2, midY);
        head.addPoint(x2 - 7, midY - 4);
        head.addPoint(x2 - 7, midY + 4);
        g2.fillPolygon(head);
    }
}
