package urbanroute.control;

import urbanroute.estructuras.ListaEnlazada;
import urbanroute.estructuras.Nodo;
import urbanroute.estructuras.TablaHash;
import urbanroute.modelo.Conexion;
import urbanroute.modelo.Estacion;
import urbanroute.modelo.Grafo;

/**
 * Calcula la ruta de menor tiempo entre dos estaciones con el algoritmo de
 * Dijkstra (version con arreglos, O(V^2), suficiente para una red urbana).
 */
public class CalculadorRutas {

    /** Resultado de un calculo de ruta. */
    public static class Ruta {
        private final ListaEnlazada<Estacion> recorrido;
        private final double tiempoTotal;

        Ruta(ListaEnlazada<Estacion> recorrido, double tiempoTotal) {
            this.recorrido = recorrido;
            this.tiempoTotal = tiempoTotal;
        }

        /** true si existe un camino entre las estaciones. */
        public boolean existe() {
            return !recorrido.estaVacia();
        }

        public ListaEnlazada<Estacion> getRecorrido() {
            return recorrido;
        }

        public double getTiempoTotal() {
            return tiempoTotal;
        }

        /** Cantidad de tramos recorridos. */
        public int getTramos() {
            return Math.max(0, recorrido.getTamano() - 1);
        }
    }

    private final Grafo grafo;

    public CalculadorRutas(Grafo grafo) {
        this.grafo = grafo;
    }

    /**
     * Ruta mas rapida entre dos estaciones.
     *
     * @return la ruta; si no hay camino, {@link Ruta#existe()} es false
     * @throws IllegalArgumentException si alguna estacion no existe
     */
    public Ruta calcularRuta(String idOrigen, String idDestino) {
        Estacion origen = grafo.buscarEstacion(idOrigen);
        Estacion destino = grafo.buscarEstacion(idDestino);
        if (origen == null) {
            throw new IllegalArgumentException("La estacion de origen '" + idOrigen + "' no existe.");
        }
        if (destino == null) {
            throw new IllegalArgumentException("La estacion de destino '" + idDestino + "' no existe.");
        }

        // 1. Numerar las estaciones para usar arreglos
        int n = grafo.getNumEstaciones();
        Estacion[] estacion = new Estacion[n];
        TablaHash<Integer> indice = new TablaHash<>(n * 2 + 1);
        int k = 0;
        for (Nodo<Estacion> e = grafo.getEstaciones().getPrimero(); e != null; e = e.getSiguiente()) {
            estacion[k] = e.getDato();
            indice.insertar(e.getDato().getId(), k);
            k++;
        }

        double[] dist = new double[n];
        int[] previo = new int[n];
        boolean[] listo = new boolean[n];
        for (int i = 0; i < n; i++) {
            dist[i] = Double.POSITIVE_INFINITY;
            previo[i] = -1;
        }
        int s = indice.buscar(origen.getId());
        int t = indice.buscar(destino.getId());
        dist[s] = 0;

        // 2. Dijkstra
        for (int paso = 0; paso < n; paso++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!listo[i] && (u == -1 || dist[i] < dist[u])) {
                    u = i;
                }
            }
            if (u == -1 || dist[u] == Double.POSITIVE_INFINITY) {
                break; // lo que queda no es alcanzable
            }
            listo[u] = true;
            if (u == t) {
                break; // ya tenemos la ruta al destino
            }
            for (Nodo<Conexion> c = grafo.getVecinos(estacion[u].getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                int v = indice.buscar(c.getDato().getDestino().getId());
                double nuevaDist = dist[u] + c.getDato().getTiempo();
                if (!listo[v] && nuevaDist < dist[v]) {
                    dist[v] = nuevaDist;
                    previo[v] = u;
                }
            }
        }

        // 3. Reconstruir el camino desde el destino hacia atras
        ListaEnlazada<Estacion> recorrido = new ListaEnlazada<>();
        if (dist[t] == Double.POSITIVE_INFINITY) {
            return new Ruta(recorrido, -1); // no hay ruta posible
        }
        for (int v = t; v != -1; v = previo[v]) {
            recorrido.agregarAlInicio(estacion[v]);
        }
        return new Ruta(recorrido, dist[t]);
    }

    /** Texto con el resultado listo para mostrar al usuario. */
    public String describir(Ruta ruta, String idOrigen, String idDestino) {
        Estacion o = grafo.buscarEstacion(idOrigen);
        Estacion d = grafo.buscarEstacion(idDestino);
        StringBuilder sb = new StringBuilder();
        if (!ruta.existe()) {
            sb.append("No hay ruta posible entre ").append(o).append(" y ").append(d)
              .append(".\nEstan en subredes distintas (no conectadas).");
            return sb.toString();
        }
        sb.append("Ruta mas rapida: ").append(o.getNombre()).append(" -> ").append(d.getNombre()).append("\n");
        sb.append("Tiempo total: ").append(formatear(ruta.getTiempoTotal())).append(" min");
        sb.append("  |  Tramos: ").append(ruta.getTramos()).append("\n\n");
        int i = 1;
        Estacion anterior = null;
        for (Nodo<Estacion> e = ruta.getRecorrido().getPrimero(); e != null; e = e.getSiguiente()) {
            Estacion actual = e.getDato();
            sb.append(i++).append(". ").append(actual.getNombre());
            if (!actual.getLinea().isEmpty()) {
                sb.append(" [").append(actual.getLinea()).append("]");
            }
            if (anterior != null) {
                sb.append("  (+").append(formatear(grafo.getTiempo(anterior.getId(), actual.getId()))).append(" min)");
                if (!anterior.getLinea().equalsIgnoreCase(actual.getLinea())) {
                    sb.append("  << transbordo");
                }
            }
            sb.append("\n");
            anterior = actual;
        }
        return sb.toString();
    }

    static String formatear(double x) {
        if (x == Math.floor(x)) {
            return String.valueOf((long) x);
        }
        return String.format("%.1f", x);
    }
}
