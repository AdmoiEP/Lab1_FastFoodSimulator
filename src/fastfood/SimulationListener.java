package fastfood;

/**
 * Receives a fresh snapshot after each simulation event.
 * The simulation does not know about buttons or panels.
 */
public interface SimulationListener {
    void onUpdate(ViewState state);
}
