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

    public VisualizadorGrafo(Grafo grafo) {
        this.grafo = grafo;
        setBackground(Color.WHITE);
        ToolTipManager.sharedInstance().registerComponent(this);
        instalarRaton();
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

    /** Recalcula la distribucion completa de la red y ajusta la vista. */
    public void reorganizar() {
        calcularDistribucion();
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
    // Distribucion (layout por fuerzas)
    // ------------------------------------------------------------------

    private void calcularDistribucion() {
        int n = grafo.getNumEstaciones();
        if (n == 0) {
            return;
        }
        Estacion[] est = new Estacion[n];
        TablaHash<Integer> idx = new TablaHash<>(n * 2 + 1);
        int k = 0;
        for (Nodo<Estacion> e = grafo.getEstaciones().getPrimero(); e != null; e = e.getSiguiente()) {
            est[k] = e.getDato();
            idx.insertar(e.getDato().getId(), k);
            k++;
        }
        double ancho = 1000, alto = 700;
        double area = ancho * alto;
        double kOpt = Math.sqrt(area / n) * 0.9;
        double[] x = new double[n], y = new double[n];
        // Posicion inicial: en circulo (resultado estable y repetible)
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n;
            x[i] = ancho / 2 + Math.cos(ang) * ancho / 3;
            y[i] = alto / 2 + Math.sin(ang) * alto / 3;
        }
        double temperatura = ancho / 8;
        int iteraciones = 400;
        double[] dx = new double[n], dy = new double[n];
        for (int it = 0; it < iteraciones; it++) {
            for (int i = 0; i < n; i++) {
                dx[i] = 0;
                dy[i] = 0;
            }
            // Repulsion entre todos los pares
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double ddx = x[i] - x[j], ddy = y[i] - y[j];
                    double d = Math.max(0.01, Math.sqrt(ddx * ddx + ddy * ddy));
                    double f = kOpt * kOpt / d;
                    dx[i] += ddx / d * f;
                    dy[i] += ddy / d * f;
                    dx[j] -= ddx / d * f;
                    dy[j] -= ddy / d * f;
                }
            }
            // Atraccion en las conexiones
            for (int i = 0; i < n; i++) {
                for (Nodo<Conexion> c = grafo.getVecinos(est[i].getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                    int j = idx.buscar(c.getDato().getDestino().getId());
                    if (j <= i) {
                        continue;
                    }
                    double ddx = x[i] - x[j], ddy = y[i] - y[j];
                    double d = Math.max(0.01, Math.sqrt(ddx * ddx + ddy * ddy));
                    double f = d * d / kOpt;
                    dx[i] -= ddx / d * f;
                    dy[i] -= ddy / d * f;
                    dx[j] += ddx / d * f;
                    dy[j] += ddy / d * f;
                }
            }
            // Gravedad leve al centro (para que las estaciones sueltas no se alejen)
            for (int i = 0; i < n; i++) {
                dx[i] -= (x[i] - ancho / 2) * 0.02 * kOpt / 10;
                dy[i] -= (y[i] - alto / 2) * 0.02 * kOpt / 10;
                double d = Math.max(0.01, Math.sqrt(dx[i] * dx[i] + dy[i] * dy[i]));
                double paso = Math.min(d, temperatura);
                x[i] += dx[i] / d * paso;
                y[i] += dy[i] / d * paso;
            }
            temperatura *= 0.985;
        }
        posiciones.vaciar();
        for (int i = 0; i < n; i++) {
            posiciones.insertar(est[i].getId(), new double[]{x[i], y[i]});
        }
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
        double margen = 60;
        double ew = (w - 2 * margen) / Math.max(1, maxX - minX);
        double eh = (h - 2 * margen) / Math.max(1, maxY - minY);
        escala = Math.max(0.2, Math.min(2.5, Math.min(ew, eh)));
        despX = w / 2.0 - (minX + maxX) / 2 * escala;
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
                if (mostrarTiempos) {
                    g.setColor(Color.DARK_GRAY);
                    String t = formatear(c.getDato().getTiempo());
                    g.drawString(t, (float) ((pa[0] + pb[0]) / 2 + 3), (float) ((pa[1] + pb[1]) / 2 - 3));
                }
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

            g.setFont(enRuta || esHub ? fuenteNegrita : fuente);
            g.setColor(new Color(0x33, 0x33, 0x33));
            g.drawString(e.getNombre(), (float) (p[0] + r + 3), (float) (p[1] + 4));
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
        return x == Math.floor(x) ? String.valueOf((long) x) : String.format("%.1f", x);
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
                    despX += e.getX() - ultimoX;
                    despY += e.getY() - ultimoY;
                }
                ultimoX = e.getX();
                ultimoY = e.getY();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15;
                double nueva = Math.max(0.1, Math.min(6, escala * factor));
                factor = nueva / escala;
                despX = e.getX() - (e.getX() - despX) * factor;
                despY = e.getY() - (e.getY() - despY) * factor;
                escala = nueva;
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
