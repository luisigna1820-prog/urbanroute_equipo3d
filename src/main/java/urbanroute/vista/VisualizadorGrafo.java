package urbanroute.vista;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import urbanroute.estructuras.ListaEnlazada;
import urbanroute.estructuras.Nodo;
import urbanroute.estructuras.TablaHash;
import urbanroute.modelo.Conexion;
import urbanroute.modelo.Estacion;
import urbanroute.modelo.Grafo;

/**
 * Panel que dibuja la red. Las posiciones se calculan con un algoritmo de
 * fuerzas (Fruchterman-Reingold): las conexiones atraen y las estaciones se
 * repelen, asi la red queda legible sin coordenadas en el archivo.
 * <p>
 * Controles: arrastrar una estacion la mueve, arrastrar el fondo desplaza la
 * vista, la rueda del raton hace zoom, pasar el raton muestra la info.
 */
public class VisualizadorGrafo extends JPanel {

    /** Modo de color de las estaciones. */
    public enum ModoColor { LINEA, SUBRED }

    private static final Color[] PALETA = {
        new Color(0xE3, 0x1B, 0x23), new Color(0x00, 0x6E, 0xB8), new Color(0x2E, 0x9E, 0x44),
        new Color(0xF2, 0x8C, 0x00), new Color(0x8E, 0x44, 0xAD), new Color(0x00, 0x9C, 0xA6),
        new Color(0xC2, 0x18, 0x5B), new Color(0x79, 0x55, 0x48), new Color(0x60, 0x7D, 0x8B),
        new Color(0xAF, 0xB4, 0x2B), new Color(0x3F, 0x51, 0xB5), new Color(0xFF, 0x57, 0x22)
    };
    private static final double RADIO = 9;

    private Grafo grafo;
    private final TablaHash<double[]> posiciones = new TablaHash<>();
    private final TablaHash<Color> colorLinea = new TablaHash<>();
    private TablaHash<Integer> subredDe = new TablaHash<>();
    private ModoColor modo = ModoColor.LINEA;

    private ListaEnlazada<Estacion> rutaResaltada = new ListaEnlazada<>();
    private ListaEnlazada<Estacion> hubsResaltados = new ListaEnlazada<>();
    private boolean mostrarTiempos = false;

    // Vista (zoom y desplazamiento)
    private double escala = 1.0;
    private double despX = 0, despY = 0;
    private Estacion arrastrada = null;
    private int ultimoX, ultimoY;
    /** La vista se ajusta en el primer dibujo, cuando ya se conoce el tamano del panel. */
    private boolean vistaPendiente = true;
    /** Pasa a true cuando el usuario hace zoom o desplaza la vista. */
    private boolean vistaManual = false;

    public VisualizadorGrafo(Grafo grafo) {
        this.grafo = grafo;
        setBackground(Color.WHITE);
        ToolTipManager.sharedInstance().registerComponent(this);
        instalarRaton();
        // Si cambia el tamano del panel, el dibujo se vuelve a ajustar (salvo que el usuario haya hecho zoom)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!vistaManual) {
                    ajustarVista();
                    repaint();
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // API publica
    // ------------------------------------------------------------------

    /** Cambia la red mostrada (al cargar un archivo nuevo) y recalcula todo. */
    public void setGrafo(Grafo grafo) {
        this.grafo = grafo;
        posiciones.vaciar();
        colorLinea.vaciar();
        limpiarResaltado();
        reorganizar();
    }

    /** Debe llamarse cuando la red cambia (altas o bajas). */
    public void actualizar(TablaHash<Integer> subredes) {
        this.subredDe = subredes;
        // Quitar posiciones de estaciones eliminadas y ubicar las nuevas cerca de sus vecinos
        ListaEnlazada<Estacion> nuevas = new ListaEnlazada<>();
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            if (!posiciones.contiene(n.getDato().getId())) {
                nuevas.agregar(n.getDato());
            }
        }
        limpiarPosicionesHuerfanas();
        int k = 0;
        for (Nodo<Estacion> n = nuevas.getPrimero(); n != null; n = n.getSiguiente()) {
            double[] p = posicionCercaDeVecinos(n.getDato(), k++);
            posiciones.insertar(n.getDato().getId(), p);
        }
        repaint();
    }

    /** Vuelve a encuadrar toda la red en el panel (deshace el zoom y el desplazamiento). */
    public void ajustarVistaCompleta() {
        vistaManual = false;
        ajustarVista();
        repaint();
    }

    /** Recalcula la distribucion completa de la red y ajusta la vista. */
    public void reorganizar() {
        calcularDistribucion();
        vistaManual = false;
        if (getWidth() > 0) {
            ajustarVista();
        } else {
            vistaPendiente = true;
        }
        repaint();
    }

    public void setModoColor(ModoColor modo) {
        this.modo = modo;
        repaint();
    }

    public void setSubredes(TablaHash<Integer> subredes) {
        this.subredDe = subredes;
        repaint();
    }

    public void setMostrarTiempos(boolean mostrar) {
        this.mostrarTiempos = mostrar;
        repaint();
    }

    public void resaltarRuta(ListaEnlazada<Estacion> ruta) {
        this.rutaResaltada = ruta == null ? new ListaEnlazada<>() : ruta;
        repaint();
    }

    public void resaltarHubs(Estacion[] hubs) {
        hubsResaltados = new ListaEnlazada<>();
        if (hubs != null) {
            for (Estacion h : hubs) {
                hubsResaltados.agregar(h);
            }
        }
        repaint();
    }

    public void limpiarResaltado() {
        rutaResaltada = new ListaEnlazada<>();
        hubsResaltados = new ListaEnlazada<>();
        repaint();
    }

    // ------------------------------------------------------------------
    // Distribucion: esquema tipo mapa del Metro
    // ------------------------------------------------------------------

    private static final double SEPARACION_COLUMNAS = 330;
    /** Estaciones cuyo nombre se escribe a la izquierda (columnas de la izquierda). */
    private final TablaHash<Boolean> etiquetaIzquierda = new TablaHash<>();
    private static final double SEPARACION_FILAS = 40;
    /** Distancia horizontal entre una linea y sus ramales (por ejemplo Zoologico). */
    private static final double SEPARACION_RAMAL = 260;

    /**
     * Dibuja cada linea como una columna recta, igual que un mapa del Metro.
     * <ul>
     *   <li>Las estaciones de cada linea van en el orden de sus conexiones.</li>
     *   <li>La linea con mas transbordos va en el centro y las demas a los lados.</li>
     *   <li>Cada columna se sube o baja para que el transbordo quede a la misma
     *       altura que la estacion con la que conecta (linea horizontal).</li>
     *   <li>Los ramales van en una columna corrida, junto a la estacion de la que salen.</li>
     *   <li>Las estaciones sin conexiones quedan al final de su columna.</li>
     * </ul>
     */
    private void calcularDistribucion() {
        posiciones.vaciar();
        etiquetaIzquierda.vaciar();

        // Lineas existentes, ordenadas de mas a menos transbordos
        ListaEnlazada<String> lineas = new ListaEnlazada<>();
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            String l = nombreLinea(n.getDato());
            if (!lineas.contiene(l)) {
                lineas.agregar(l);
            }
        }
        String[] orden = new String[lineas.getTamano()];
        int k = 0;
        for (Nodo<String> l = lineas.getPrimero(); l != null; l = l.getSiguiente()) {
            orden[k++] = l.getDato();
        }
        for (int i = 1; i < orden.length; i++) { // insercion, de mayor a menor
            String x = orden[i];
            int j = i - 1;
            while (j >= 0 && contarTransbordos(orden[j]) < contarTransbordos(x)) {
                orden[j + 1] = orden[j];
                j--;
            }
            orden[j + 1] = x;
        }

        // Ubicar cada linea: centro, derecha, izquierda, derecha, izquierda...
        for (int i = 0; i < orden.length; i++) {
            int columna = (i == 0) ? 0 : (i % 2 == 1 ? (i + 1) / 2 : -(i / 2));
            ListaEnlazada<ListaEnlazada<Estacion>> tramos = new ListaEnlazada<>();
            ListaEnlazada<Estacion> aisladas = new ListaEnlazada<>();
            ordenarLinea(orden[i], tramos, aisladas);
            double x = columna * SEPARACION_COLUMNAS;
            // Los ramales van del lado de los nombres, mas alla de ellos
            int lado = columna < 0 ? -1 : 1;
            int ultimaFila = 0;

            boolean primero = true;
            for (Nodo<ListaEnlazada<Estacion>> t = tramos.getPrimero(); t != null; t = t.getSiguiente()) {
                ListaEnlazada<Estacion> tramo = t.getDato();
                if (primero) {
                    // Tramo principal: alineado con el transbordo
                    int fila = calcularDesplazamiento(tramo);
                    for (Nodo<Estacion> e = tramo.getPrimero(); e != null; e = e.getSiguiente()) {
                        ubicar(e.getDato(), x, fila, columna);
                        ultimaFila = Math.max(ultimaFila, fila);
                        fila++;
                    }
                    primero = false;
                    continue;
                }
                // Ramal: columna corrida, dos filas debajo de la estacion de la que sale
                int filaUnion = filaDeUnion(tramo.getPrimero().getDato());
                if (filaUnion == Integer.MIN_VALUE) {
                    // No se une a nada ya ubicado: va debajo del tramo principal
                    int fila = ultimaFila + 1;
                    for (Nodo<Estacion> e = tramo.getPrimero(); e != null; e = e.getSiguiente()) {
                        ubicar(e.getDato(), x, fila, columna);
                        ultimaFila = Math.max(ultimaFila, fila);
                        fila++;
                    }
                } else {
                    int fila = filaUnion + 2; // dos filas abajo: la union no tapa el nombre
                    for (Nodo<Estacion> e = tramo.getPrimero(); e != null; e = e.getSiguiente()) {
                        ubicar(e.getDato(), x + lado * SEPARACION_RAMAL, fila, columna);
                        ultimaFila = Math.max(ultimaFila, fila);
                        fila++;
                    }
                }
            }
            // Estaciones aisladas al final, separadas por una fila vacia
            int fila = tramos.estaVacia() ? 0 : ultimaFila + 2;
            for (Nodo<Estacion> e = aisladas.getPrimero(); e != null; e = e.getSiguiente()) {
                ubicar(e.getDato(), x, fila, columna);
                fila++;
            }
        }
    }

    /** Guarda la posicion de una estacion y de que lado va su nombre. */
    private void ubicar(Estacion e, double x, int fila, int columna) {
        posiciones.insertar(e.getId(), new double[]{x, fila * SEPARACION_FILAS});
        if (columna < 0) {
            etiquetaIzquierda.insertar(e.getId(), Boolean.TRUE);
        }
    }

    /**
     * Fila de la estacion ya ubicada de la que sale un ramal (un vecino de la
     * misma linea). Devuelve Integer.MIN_VALUE si no hay ninguna.
     */
    private int filaDeUnion(Estacion inicioRamal) {
        for (Nodo<Conexion> c = grafo.getVecinos(inicioRamal.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
            Estacion v = c.getDato().getDestino();
            double[] p = posiciones.buscar(v.getId());
            if (p != null && nombreLinea(v).equals(nombreLinea(inicioRamal))) {
                return (int) Math.round(p[1] / SEPARACION_FILAS);
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Cuantas conexiones tiene la linea con estaciones de otras lineas. */
    private int contarTransbordos(String linea) {
        int cuenta = 0;
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            if (!nombreLinea(n.getDato()).equals(linea)) {
                continue;
            }
            for (Nodo<Conexion> c = grafo.getVecinos(n.getDato().getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                if (!nombreLinea(c.getDato().getDestino()).equals(linea)) {
                    cuenta++;
                }
            }
        }
        return cuenta;
    }

    /**
     * Cuantas filas hay que bajar la columna para que su primer transbordo
     * quede a la altura de la estacion (ya ubicada) con la que conecta.
     */
    private int calcularDesplazamiento(ListaEnlazada<Estacion> estaciones) {
        int filaPropia = 0;
        for (Nodo<Estacion> e = estaciones.getPrimero(); e != null; e = e.getSiguiente()) {
            for (Nodo<Conexion> c = grafo.getVecinos(e.getDato().getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                double[] otra = posiciones.buscar(c.getDato().getDestino().getId());
                if (otra != null) {
                    int filaOtra = (int) Math.round(otra[1] / SEPARACION_FILAS);
                    return filaOtra - filaPropia;
                }
            }
            filaPropia++;
        }
        return 0;
    }

    private static String nombreLinea(Estacion e) {
        return e.getLinea().isEmpty() ? "(sin linea)" : e.getLinea();
    }

    /**
     * Separa las estaciones de una linea en tramos recorriendo sus conexiones
     * con estaciones de la misma linea. Cada tramo empieza en una punta
     * (estacion con un solo vecino de su linea) y avanza de vecino en vecino.
     * El primer tramo es el principal; los demas son ramales.
     */
    private void ordenarLinea(String linea, ListaEnlazada<ListaEnlazada<Estacion>> tramos,
            ListaEnlazada<Estacion> aisladas) {
        ListaEnlazada<Estacion> deLaLinea = new ListaEnlazada<>();
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            if (nombreLinea(n.getDato()).equals(linea)) {
                deLaLinea.agregar(n.getDato());
            }
        }
        TablaHash<Boolean> usada = new TablaHash<>();

        // Primero se empieza solo desde las puntas; luego desde cualquier estacion que falte
        for (int pasada = 0; pasada < 2; pasada++) {
            for (Nodo<Estacion> n = deLaLinea.getPrimero(); n != null; n = n.getSiguiente()) {
                Estacion e = n.getDato();
                if (usada.contiene(e.getId())) {
                    continue;
                }
                if (e.getGrado() == 0) {
                    aisladas.agregar(e);
                    usada.insertar(e.getId(), Boolean.TRUE);
                    continue;
                }
                if (pasada == 0 && vecinosDeLinea(e, linea) > 1) {
                    continue;
                }
                ListaEnlazada<Estacion> tramo = new ListaEnlazada<>();
                Estacion actual = e;
                while (actual != null) {
                    tramo.agregar(actual);
                    usada.insertar(actual.getId(), Boolean.TRUE);
                    Estacion siguiente = null;
                    for (Nodo<Conexion> c = grafo.getVecinos(actual.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                        Estacion v = c.getDato().getDestino();
                        if (nombreLinea(v).equals(linea) && !usada.contiene(v.getId())) {
                            siguiente = v;
                            break;
                        }
                    }
                    actual = siguiente;
                }
                tramos.agregar(tramo);
            }
        }
    }

    /** Cuantos vecinos de la misma linea tiene una estacion. */
    private int vecinosDeLinea(Estacion e, String linea) {
        int cuenta = 0;
        for (Nodo<Conexion> c = grafo.getVecinos(e.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
            if (nombreLinea(c.getDato().getDestino()).equals(linea)) {
                cuenta++;
            }
        }
        return cuenta;
    }

    /**
     * Posicion para una estacion nueva: el promedio de sus vecinos ya dibujados,
     * corrido un poco para no tapar a nadie. Si no tiene vecinos va al centro.
     * "k" es el numero de estacion nueva y sirve para que no queden encimadas.
     */
    private double[] posicionCercaDeVecinos(Estacion e, int k) {
        double corrimiento = 30 + 15 * (k % 4);
        double sx = 0, sy = 0;
        int cuenta = 0;
        for (Nodo<Conexion> c = grafo.getVecinos(e.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
            double[] p = posiciones.buscar(c.getDato().getDestino().getId());
            if (p != null) {
                sx += p[0];
                sy += p[1];
                cuenta++;
            }
        }
        if (cuenta > 0) {
            return new double[]{sx / cuenta + corrimiento, sy / cuenta + corrimiento};
        }
        // Sin vecinos: en el centro de lo que se ve
        double cx = (getWidth() / 2.0 - despX) / escala;
        double cy = (getHeight() / 2.0 - despY) / escala;
        return new double[]{cx + corrimiento * (k % 3), cy + corrimiento * (k / 3 % 3)};
    }

    private void limpiarPosicionesHuerfanas() {
        TablaHash<double[]> vivas = new TablaHash<>();
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            double[] p = posiciones.buscar(n.getDato().getId());
            if (p != null) {
                vivas.insertar(n.getDato().getId(), p);
            }
        }
        posiciones.vaciar();
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            double[] p = vivas.buscar(n.getDato().getId());
            if (p != null) {
                posiciones.insertar(n.getDato().getId(), p);
            }
        }
    }

    /** Ajusta zoom y desplazamiento para que toda la red quepa en el panel. */
    private void ajustarVista() {
        if (grafo.estaVacio()) {
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            double[] p = posiciones.buscar(n.getDato().getId());
            if (p == null) {
                continue;
            }
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        int w = getWidth() > 0 ? getWidth() : 900;
        int h = getHeight() > 0 ? getHeight() : 600;
        // Margenes: a la derecha mas espacio porque ahi van los nombres
        double izq = 160, der = 160, vertical = 40;
        double ew = (w - izq - der) / Math.max(1, maxX - minX);
        double eh = (h - 2 * vertical) / Math.max(1, maxY - minY);
        escala = Math.max(0.05, Math.min(2.5, Math.min(ew, eh)));
        despX = izq + (w - izq - der) / 2.0 - (minX + maxX) / 2 * escala;
        despY = h / 2.0 - (minY + maxY) / 2 * escala;
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (grafo == null || grafo.estaVacio()) {
            g.setColor(Color.GRAY);
            g.setFont(getFont().deriveFont(Font.PLAIN, 15f));
            String msg = "No hay red cargada. Use \"Cargar archivo\" para abrir diccionario.csv y grafo.csv";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
            g.dispose();
            return;
        }

        if (vistaPendiente && getWidth() > 0) {
            ajustarVista();
            vistaPendiente = false;
        }
        // Las posiciones se transforman a pantalla; circulos y textos mantienen su tamano
        dibujarConexiones(g);
        dibujarRuta(g);
        dibujarEstaciones(g);
        if (mostrarTiempos) {
            dibujarTiempos(g);
        }
        dibujarLeyenda(g);
        g.dispose();
    }

    /** Convierte una posicion del modelo a coordenadas de pantalla. */
    private double[] aPantalla(double[] p) {
        return p == null ? null : new double[]{p[0] * escala + despX, p[1] * escala + despY};
    }

    private void dibujarConexiones(Graphics2D g) {
        g.setStroke(new BasicStroke(2.5f));
        Font fuenteTiempo = getFont().deriveFont(Font.PLAIN, 10f);
        g.setFont(fuenteTiempo);
        ListaEnlazada<Estacion> dibujadas = new ListaEnlazada<>();
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion a = n.getDato();
            double[] pa = aPantalla(posiciones.buscar(a.getId()));
            if (pa == null) {
                continue;
            }
            for (Nodo<Conexion> c = grafo.getVecinos(a.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                Estacion b = c.getDato().getDestino();
                if (dibujadas.contiene(b)) {
                    continue; // ya se dibujo desde el otro extremo
                }
                double[] pb = aPantalla(posiciones.buscar(b.getId()));
                if (pb == null) {
                    continue;
                }
                boolean mismaLinea = a.getLinea().equalsIgnoreCase(b.getLinea());
                Color col = (modo == ModoColor.LINEA && mismaLinea) ? aclarar(colorDe(a), 0.35) : new Color(0xB0, 0xB0, 0xB0);
                g.setColor(col);
                g.draw(new Line2D.Double(pa[0], pa[1], pb[0], pb[1]));
            }
            dibujadas.agregar(a);
        }
    }

    /**
     * Escribe el tiempo de cada tramo despues de dibujar las estaciones, para
     * que no quede tapado: al costado de los tramos verticales y encima de los
     * horizontales, con un borde blanco para que se lea sobre las lineas.
     */
    private void dibujarTiempos(Graphics2D g) {
        g.setFont(getFont().deriveFont(Font.BOLD, 10f));
        FontMetrics fm = g.getFontMetrics();
        ListaEnlazada<Estacion> dibujadas = new ListaEnlazada<>();
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion a = n.getDato();
            double[] pa = aPantalla(posiciones.buscar(a.getId()));
            if (pa == null) {
                continue;
            }
            for (Nodo<Conexion> c = grafo.getVecinos(a.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                Estacion b = c.getDato().getDestino();
                double[] pb = aPantalla(posiciones.buscar(b.getId()));
                if (dibujadas.contiene(b) || pb == null) {
                    continue;
                }
                String t = formatear(c.getDato().getTiempo());
                int ancho = fm.stringWidth(t);
                double mx = (pa[0] + pb[0]) / 2, my = (pa[1] + pb[1]) / 2;
                int tx, ty;
                if (Math.abs(pb[0] - pa[0]) < Math.abs(pb[1] - pa[1])) {
                    // Tramo vertical: al lado contrario de los nombres
                    boolean nombresIzq = etiquetaIzquierda.contiene(a.getId());
                    tx = nombresIzq ? (int) (mx + 6) : (int) (mx - 6 - ancho);
                    ty = (int) (my + 4);
                } else {
                    // Tramo horizontal: encima de la linea
                    tx = (int) (mx - ancho / 2.0);
                    ty = (int) (my - 6);
                }
                g.setColor(Color.WHITE);
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oy = -1; oy <= 1; oy++) {
                        g.drawString(t, tx + ox, ty + oy);
                    }
                }
                g.setColor(new Color(0x1E, 0x40, 0xAF));
                g.drawString(t, tx, ty);
            }
            dibujadas.agregar(a);
        }
    }

    private void dibujarRuta(Graphics2D g) {
        if (rutaResaltada.getTamano() < 2) {
            return;
        }
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 196, 0, 220));
        Estacion anterior = null;
        for (Nodo<Estacion> n = rutaResaltada.getPrimero(); n != null; n = n.getSiguiente()) {
            if (anterior != null) {
                double[] pa = aPantalla(posiciones.buscar(anterior.getId()));
                double[] pb = aPantalla(posiciones.buscar(n.getDato().getId()));
                if (pa != null && pb != null) {
                    g.draw(new Line2D.Double(pa[0], pa[1], pb[0], pb[1]));
                }
            }
            anterior = n.getDato();
        }
    }

    private void dibujarEstaciones(Graphics2D g) {
        Font fuente = getFont().deriveFont(Font.PLAIN, 11f);
        Font fuenteNegrita = getFont().deriveFont(Font.BOLD, 12f);
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion e = n.getDato();
            double[] p = aPantalla(posiciones.buscar(e.getId()));
            if (p == null) {
                continue;
            }
            boolean enRuta = rutaResaltada.contiene(e);
            boolean esHub = hubsResaltados.contiene(e);
            double r = esHub ? RADIO * 1.6 : RADIO;

            if (esHub) {
                g.setColor(new Color(255, 196, 0, 120));
                g.fill(new Ellipse2D.Double(p[0] - r - 5, p[1] - r - 5, 2 * r + 10, 2 * r + 10));
            }
            g.setColor(colorDe(e));
            g.fill(new Ellipse2D.Double(p[0] - r, p[1] - r, 2 * r, 2 * r));
            g.setStroke(new BasicStroke(enRuta ? 3f : 1.5f));
            g.setColor(enRuta ? Color.BLACK : Color.WHITE);
            g.draw(new Ellipse2D.Double(p[0] - r, p[1] - r, 2 * r, 2 * r));
        }
        // Nombres encima de todo, con un borde blanco para que se lean sobre las lineas
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion e = n.getDato();
            double[] p = aPantalla(posiciones.buscar(e.getId()));
            if (p == null) {
                continue;
            }
            boolean resaltada = rutaResaltada.contiene(e) || hubsResaltados.contiene(e);
            double r = hubsResaltados.contiene(e) ? RADIO * 1.6 : RADIO;
            g.setFont(resaltada ? fuenteNegrita : fuente);
            int ancho = g.getFontMetrics().stringWidth(e.getNombre());
            int tx = etiquetaIzquierda.contiene(e.getId())
                    ? (int) (p[0] - r - 5 - ancho) : (int) (p[0] + r + 5);
            int ty = (int) (p[1] + 4);
            g.setColor(Color.WHITE);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    g.drawString(e.getNombre(), tx + ox, ty + oy);
                }
            }
            g.setColor(new Color(0x33, 0x33, 0x33));
            g.drawString(e.getNombre(), tx, ty);
        }
    }

    private void dibujarLeyenda(Graphics2D g) {
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        int x = 12, y = 20;
        g.setColor(new Color(0x55, 0x55, 0x55));
        g.drawString(modo == ModoColor.LINEA ? "Color por linea" : "Color por subred", x, y);
        y += 18;
        if (modo == ModoColor.LINEA) {
            ListaEnlazada<String> vistas = new ListaEnlazada<>();
            for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null && vistas.getTamano() < 14; n = n.getSiguiente()) {
                String l = n.getDato().getLinea().isEmpty() ? "(sin linea)" : n.getDato().getLinea();
                if (vistas.contiene(l)) {
                    continue;
                }
                vistas.agregar(l);
                g.setColor(colorDe(n.getDato()));
                g.fillOval(x, y - 10, 11, 11);
                g.setColor(new Color(0x33, 0x33, 0x33));
                g.drawString(l, x + 17, y);
                y += 17;
            }
        } else {
            int total = 0;
            for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
                Integer s = subredDe.buscar(n.getDato().getId());
                if (s != null && s + 1 > total) {
                    total = s + 1;
                }
            }
            g.setColor(new Color(0x33, 0x33, 0x33));
            g.drawString(total + " subred(es)", x, y);
        }
    }

    // ------------------------------------------------------------------
    // Colores
    // ------------------------------------------------------------------

    private Color colorDe(Estacion e) {
        if (modo == ModoColor.SUBRED) {
            Integer s = subredDe.buscar(e.getId());
            return s == null ? Color.GRAY : PALETA[s % PALETA.length];
        }
        String linea = e.getLinea().isEmpty() ? "(sin linea)" : e.getLinea();
        Color c = colorLinea.buscar(linea);
        if (c == null) {
            c = colorPorNombreDeLinea(linea);
            colorLinea.insertar(linea, c);
        }
        return c;
    }

    /** Si la linea se llama "L1", "Linea 2", etc. usa un color fijo; si no, el siguiente de la paleta. */
    private Color colorPorNombreDeLinea(String linea) {
        String digitos = linea.replaceAll("[^0-9]", "");
        if (!digitos.isEmpty() && digitos.length() <= 2) {
            return PALETA[(Integer.parseInt(digitos) + PALETA.length - 1) % PALETA.length];
        }
        return PALETA[(colorLinea.getTamano() + 4) % PALETA.length];
    }

    private static Color aclarar(Color c, double f) {
        return new Color(
                (int) (c.getRed() + (255 - c.getRed()) * f),
                (int) (c.getGreen() + (255 - c.getGreen()) * f),
                (int) (c.getBlue() + (255 - c.getBlue()) * f));
    }

    private static String formatear(double x) {
        return new java.math.BigDecimal(Double.toString(x)).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------
    // Raton
    // ------------------------------------------------------------------

    private Estacion estacionEn(int sx, int sy) {
        Estacion mejor = null;
        double mejorD = (RADIO + 5) * (RADIO + 5);
        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            double[] p = aPantalla(posiciones.buscar(n.getDato().getId()));
            if (p == null) {
                continue;
            }
            double d = (p[0] - sx) * (p[0] - sx) + (p[1] - sy) * (p[1] - sy);
            if (d < mejorD) {
                mejorD = d;
                mejor = n.getDato();
            }
        }
        return mejor;
    }

    private void instalarRaton() {
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                arrastrada = estacionEn(e.getX(), e.getY());
                ultimoX = e.getX();
                ultimoY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                arrastrada = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (arrastrada != null) {
                    double[] p = posiciones.buscar(arrastrada.getId());
                    if (p != null) {
                        p[0] = (e.getX() - despX) / escala;
                        p[1] = (e.getY() - despY) / escala;
                    }
                } else {
                    vistaManual = true;
                    despX += e.getX() - ultimoX;
                    despY += e.getY() - ultimoY;
                }
                ultimoX = e.getX();
                ultimoY = e.getY();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (!e.isControlDown()) {
                    return; // sin Ctrl la rueda no hace zoom
                }
                double factor = e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15;
                double nueva = Math.max(0.1, Math.min(6, escala * factor));
                factor = nueva / escala;
                despX = e.getX() - (e.getX() - despX) * factor;
                despY = e.getY() - (e.getY() - despY) * factor;
                escala = nueva;
                vistaManual = true;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Estacion est = estacionEn(e.getX(), e.getY());
                setToolTipText(est == null ? null
                        : "<html>" + est.getInformacion().replace("\n", "<br>") + "</html>");
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }
}
