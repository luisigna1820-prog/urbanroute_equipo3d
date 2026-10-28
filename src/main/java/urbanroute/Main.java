package urbanroute;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import urbanroute.vista.VentanaPrincipal;

/**
 * Punto de entrada de UrbanRoute.
 */
public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // si falla se usa el aspecto por defecto de Swing
        }
        SwingUtilities.invokeLater(() -> new VentanaPrincipal().setVisible(true));
    }
}
