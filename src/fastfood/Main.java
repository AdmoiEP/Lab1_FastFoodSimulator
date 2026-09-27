package fastfood;

import java.awt.Color;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // The default look is used when the system one is unavailable.
            }
            UIManager.put("TextField.inactiveBackground", Color.WHITE);
            new SimulatorFrame().setVisible(true);
        });
    }
}
