package urbanroute.vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import urbanroute.control.AnalizadorRed;
import urbanroute.control.CalculadorRutas;
import urbanroute.control.GestorArchivo;
import urbanroute.estructuras.ListaEnlazada;
import urbanroute.estructuras.Nodo;
import urbanroute.modelo.Estacion;
import urbanroute.modelo.Grafo;

/**
 * Pantalla principal. Solo recibe lo que pide el usuario, valida la entrada y
 * muestra resultados; los calculos los hacen las clases de control.
 */
public class VentanaPrincipal extends JFrame {

    private Grafo grafo = new Grafo();
    private final GestorArchivo gestor = new GestorArchivo();
    private AnalizadorRed analizador = new AnalizadorRed(grafo);
    private CalculadorRutas calculador = new CalculadorRutas(grafo);

    private final VisualizadorGrafo visualizador = new VisualizadorGrafo(grafo);
    private final JTextArea areaResultados = new JTextArea();
    private final JLabel etiquetaEstado = new JLabel();
    private final JComboBox<String> comboColor = new JComboBox<>(new String[]{"Color por linea", "Color por subred"});
    private boolean hayCambiosSinGuardar = false;

    public VentanaPrincipal() {
        super("UrbanRoute - Red de transporte multimodal");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        construirInterfaz();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmarDescartarCambios()) {
                    dispose();
                    System.exit(0);
                }
            }
        });
        cargarArchivoMaestro();
    }

    // ------------------------------------------------------------------
    // Interfaz
    // ------------------------------------------------------------------

    private void construirInterfaz() {
        JPanel lateral = new JPanel();
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS));
        lateral.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        lateral.add(grupo("Archivo",
                boton("Cargar archivo...", e -> cargarArchivo()),
                boton("Guardar cambios", e -> guardar())));
        lateral.add(grupo("Estaciones",
                boton("Ver estacion", e -> verEstacion())));

        JCheckBox chkTiempos = new JCheckBox("Mostrar tiempos");
        chkTiempos.addActionListener(e -> visualizador.setMostrarTiempos(chkTiempos.isSelected()));
        comboColor.addActionListener(e -> visualizador.setModoColor(
                comboColor.getSelectedIndex() == 0 ? VisualizadorGrafo.ModoColor.LINEA : VisualizadorGrafo.ModoColor.SUBRED));
        lateral.add(grupo("Vista",
                comboColor,
                chkTiempos,
                boton("Reorganizar dibujo", e -> visualizador.reorganizar()),
                boton("Quitar resaltado", e -> visualizador.limpiarResaltado())));
        lateral.add(grupo("Ayuda", boton("Como usar", e -> mostrarAyuda())));
        lateral.add(Box.createVerticalGlue());

        areaResultados.setEditable(false);
        areaResultados.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        areaResultados.setLineWrap(true);
        areaResultados.setWrapStyleWord(true);
        areaResultados.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scrollResultados = new JScrollPane(areaResultados);
        scrollResultados.setBorder(BorderFactory.createTitledBorder("Resultados"));

        JSplitPane centro = new JSplitPane(JSplitPane.VERTICAL_SPLIT, visualizador, scrollResultados);
        centro.setResizeWeight(0.75);
        centro.setDividerLocation(560);

        etiquetaEstado.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        JScrollPane scrollLateral = new JScrollPane(lateral);
        scrollLateral.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xDD, 0xDD, 0xDD)));
        scrollLateral.setPreferredSize(new Dimension(220, 0));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scrollLateral, BorderLayout.WEST);
        getContentPane().add(centro, BorderLayout.CENTER);
        getContentPane().add(etiquetaEstado, BorderLayout.SOUTH);
        actualizarEstado();
    }

    private JPanel grupo(String titulo, java.awt.Component... componentes) {
        JPanel p = new JPanel(new GridLayout(componentes.length, 1, 0, 4));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(titulo), BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        for (java.awt.Component c : componentes) {
            p.add(c);
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private JButton boton(String texto, java.awt.event.ActionListener accion) {
        JButton b = new JButton(texto);
        b.addActionListener(accion);
        return b;
    }

    // ------------------------------------------------------------------
    // Archivo (requerimientos A y C)
    // ------------------------------------------------------------------

    /** Al iniciar se carga la red de la carpeta data/ si existe. */
    private void cargarArchivoMaestro() {
        File carpeta = new File("data");
        if (!new File(carpeta, GestorArchivo.NOMBRE_DICCIONARIO).exists()) {
            mostrar("Bienvenido a UrbanRoute.\nNo se encontro el archivo maestro (data/diccionario.csv).\n"
                    + "Use \"Cargar archivo...\" para abrir una red.");
            return;
        }
        try {
            usarGrafo(gestor.cargarMaestro(carpeta));
            mostrar("Archivo maestro cargado: " + gestor.getArchivoDiccionario().getPath() + "\n"
                    + grafo.getNumEstaciones() + " estaciones y " + grafo.getNumConexiones() + " conexiones."
                    + textoAdvertencias());
        } catch (IOException ex) {
            mostrar("No se pudo cargar el archivo maestro: " + ex.getMessage());
        }
    }

    private void cargarArchivo() {
        if (!confirmarDescartarCambios()) {
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Seleccione diccionario.csv (estaciones). grafo.csv debe estar en la misma carpeta");
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos CSV", "csv"));
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] elegidos = chooser.getSelectedFiles();
        File dic = null, gra = null;
        for (File f : elegidos) {
            String n = f.getName().toLowerCase();
            if (n.contains("grafo") || n.contains("conex") || n.contains("arista")) {
                gra = f;
            } else {
                dic = f;
            }
        }
        if (dic == null) {
            error("Debe seleccionar el archivo de estaciones (diccionario.csv).");
            return;
        }
        if (gra == null) {
            File hermano = new File(dic.getParentFile(), GestorArchivo.NOMBRE_GRAFO);
            if (hermano.exists()) {
                gra = hermano;
            } else {
                JFileChooser c2 = new JFileChooser(dic.getParentFile());
                c2.setDialogTitle("Seleccione el archivo de conexiones (grafo.csv)");
                c2.setFileFilter(new FileNameExtensionFilter("Archivos CSV", "csv"));
                if (c2.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    gra = c2.getSelectedFile();
                }
            }
        }
        try {
            Grafo nuevo = gestor.cargar(dic, gra);
            grafo.vaciar(); // se descarta la red anterior
            usarGrafo(nuevo);
            mostrar("Red cargada desde " + dic.getName() + (gra != null ? " y " + gra.getName() : "") + "\n"
                    + grafo.getNumEstaciones() + " estaciones y " + grafo.getNumConexiones() + " conexiones."
                    + textoAdvertencias());
        } catch (IOException ex) {
            error("No se pudo cargar el archivo:\n" + ex.getMessage());
        }
    }

    private void guardar() {
        if (grafo.estaVacio() && !gestor.tieneArchivos()) {
            aviso("No hay nada que guardar.");
            return;
        }
        if (!gestor.tieneArchivos()) {
            JFileChooser chooser = new JFileChooser(new File("."));
            chooser.setDialogTitle("Seleccione la carpeta donde guardar diccionario.csv y grafo.csv");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            gestor.setCarpetaDestino(chooser.getSelectedFile());
        }
        int r = JOptionPane.showConfirmDialog(this,
                "Se sobrescribiran los archivos:\n" + gestor.getArchivoDiccionario().getPath() + "\n"
                + gestor.getArchivoGrafo().getPath() + "\n\nDesea continuar?",
                "Guardar cambios", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            gestor.guardar(grafo);
            hayCambiosSinGuardar = false;
            actualizarEstado();
            mostrar("Cambios guardados en " + gestor.getArchivoDiccionario().getParent());
        } catch (IOException ex) {
            error("No se pudo guardar:\n" + ex.getMessage());
        }
    }

    /** Si hay cambios sin guardar pregunta al usuario. Devuelve true si se puede continuar. */
    private boolean confirmarDescartarCambios() {
        if (!hayCambiosSinGuardar) {
            return true;
        }
        int r = JOptionPane.showConfirmDialog(this,
                "Hay cambios sin guardar en la red.\nDesea guardarlos antes de continuar?",
                "Cambios sin guardar", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) {
            guardar();
            return !hayCambiosSinGuardar;
        }
        return r == JOptionPane.NO_OPTION;
    }

    private void usarGrafo(Grafo nuevo) {
        grafo = nuevo;
        analizador = new AnalizadorRed(grafo);
        calculador = new CalculadorRutas(grafo);
        hayCambiosSinGuardar = false;
        visualizador.setGrafo(grafo);
        visualizador.setSubredes(analizador.numeroDeSubred());
        actualizarEstado();
    }

    private String textoAdvertencias() {
        ListaEnlazada<String> adv = gestor.getAdvertencias();
        if (adv.estaVacia()) {
            return "\nSin errores en los archivos.";
        }
        StringBuilder sb = new StringBuilder("\n\nAdvertencias (" + adv.getTamano() + "):\n");
        for (Nodo<String> n = adv.getPrimero(); n != null; n = n.getSiguiente()) {
            sb.append(" - ").append(n.getDato()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Consulta de estaciones
    // ------------------------------------------------------------------

    private void verEstacion() {
        if (!hayEstaciones(1)) {
            return;
        }
        JComboBox<Estacion> combo = comboEstaciones();
        if (JOptionPane.showConfirmDialog(this, combo, "Ver estacion", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Estacion e = (Estacion) combo.getSelectedItem();
        if (e == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(e.getInformacion()).append("\n\nConectada con:\n");
        for (Nodo<urbanroute.modelo.Conexion> c = grafo.getVecinos(e.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
            sb.append("  ").append(c.getDato()).append('\n');
        }
        if (analizador.contarConexiones(e.getId()) == 0) {
            sb.append("  (ninguna, es una estacion aislada)\n");
        }
        mostrar(sb.toString());
    }

    private void mostrarAyuda() {
        String ayuda = "COMO USAR URBANROUTE\n\n"
                + "1. Cargar archivo: seleccione diccionario.csv (estaciones). Si grafo.csv (conexiones) esta en\n"
                + "   la misma carpeta se carga solo; tambien puede seleccionar ambos a la vez.\n"
                + "   Formato diccionario.csv: id,nombre,linea,zona,capacidad\n"
                + "   Formato grafo.csv:       origen,destino,tiempo (IDs y minutos)\n"
                + "2. Ver estacion: muestra los datos de una estacion y sus conexiones.\n"
                + "\n"
                + "En el dibujo: arrastre una estacion para moverla, arrastre el fondo para desplazar,\n"
                + "use la rueda del raton para hacer zoom y pase el raton sobre una estacion para ver sus datos.";
        mostrar(ayuda);
    }

    // ------------------------------------------------------------------
    // Utilidades de interfaz
    // ------------------------------------------------------------------

    /** Combo con todas las estaciones ordenadas por nombre. */
    private JComboBox<Estacion> comboEstaciones() {
        Object[] objs = grafo.getEstaciones().aArreglo();
        Estacion[] arr = new Estacion[objs.length];
        for (int i = 0; i < objs.length; i++) {
            arr[i] = (Estacion) objs[i];
        }
        for (int i = 1; i < arr.length; i++) { // insercion por nombre
            Estacion x = arr[i];
            int j = i - 1;
            while (j >= 0 && arr[j].getNombre().compareToIgnoreCase(x.getNombre()) > 0) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = x;
        }
        JComboBox<Estacion> combo = new JComboBox<>(arr);
        combo.setMaximumRowCount(20);
        return combo;
    }

    private JPanel formulario(String[] etiquetas, java.awt.Component[] campos) {
        JPanel p = new JPanel(new GridLayout(etiquetas.length, 2, 8, 6));
        for (int i = 0; i < etiquetas.length; i++) {
            p.add(new JLabel(etiquetas[i]));
            p.add(campos[i]);
        }
        p.setPreferredSize(new Dimension(460, etiquetas.length * 32));
        return p;
    }

    private boolean hayEstaciones(int minimo) {
        if (grafo.getNumEstaciones() < minimo) {
            aviso(minimo == 1 ? "La red no tiene estaciones. Cargue un archivo primero."
                    : "Se necesitan al menos " + minimo + " estaciones.");
            return false;
        }
        return true;
    }

    private void actualizarEstado() {
        String archivo = gestor.getArchivoDiccionario() == null ? "sin archivo" : gestor.getArchivoDiccionario().getName();
        etiquetaEstado.setText(grafo.getNumEstaciones() + " estaciones  |  " + grafo.getNumConexiones()
                + " conexiones  |  " + archivo + (hayCambiosSinGuardar ? "  |  * cambios sin guardar" : ""));
    }

    private void mostrar(String texto) {
        areaResultados.setText(texto);
        areaResultados.setCaretPosition(0);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void aviso(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Aviso", JOptionPane.WARNING_MESSAGE);
    }
}
