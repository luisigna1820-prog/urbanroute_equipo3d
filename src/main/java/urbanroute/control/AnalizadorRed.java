package urbanroute.control;

import urbanroute.estructuras.Cola;
import urbanroute.estructuras.ListaEnlazada;
import urbanroute.estructuras.Nodo;
import urbanroute.estructuras.TablaHash;
import urbanroute.modelo.Conexion;
import urbanroute.modelo.Estacion;
import urbanroute.modelo.Grafo;

/**
 * Analisis de la red: subredes aisladas (componentes conexas, con BFS) y hubs
 * (estaciones con mas conexiones).
 */
public class AnalizadorRed {

    private final Grafo grafo;

    public AnalizadorRed(Grafo grafo) {
        this.grafo = grafo;
    }

    /**
     * Encuentra las subredes aisladas con BFS.
     * Cada subred es una lista de estaciones. Complejidad O(V + E).
     */
    public ListaEnlazada<ListaEnlazada<Estacion>> encontrarSubredes() {
        ListaEnlazada<ListaEnlazada<Estacion>> subredes = new ListaEnlazada<>();
        TablaHash<Boolean> visitado = new TablaHash<>(grafo.getNumEstaciones() * 2 + 1);

        for (Nodo<Estacion> n = grafo.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion inicio = n.getDato();
            if (visitado.contiene(inicio.getId())) {
                continue;
            }
            subredes.agregar(bfs(inicio, visitado));
        }
        return subredes;
    }

    /** Recorrido en anchura desde una estacion; marca y devuelve lo alcanzado. */
    private ListaEnlazada<Estacion> bfs(Estacion inicio, TablaHash<Boolean> visitado) {
        ListaEnlazada<Estacion> componente = new ListaEnlazada<>();
        Cola<Estacion> cola = new Cola<>();
        cola.encolar(inicio);
        visitado.insertar(inicio.getId(), Boolean.TRUE);

        while (!cola.estaVacia()) {
            Estacion actual = cola.desencolar();
            componente.agregar(actual);
            for (Nodo<Conexion> c = grafo.getVecinos(actual.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                Estacion vecino = c.getDato().getDestino();
                if (!visitado.contiene(vecino.getId())) {
                    visitado.insertar(vecino.getId(), Boolean.TRUE);
                    cola.encolar(vecino);
                }
            }
        }
        return componente;
    }

    /**
     * Numero de subred (0, 1, 2...) de cada estacion, por ID.
     * Lo usa el visualizador para colorear por subred.
     */
    public TablaHash<Integer> numeroDeSubred() {
        TablaHash<Integer> resultado = new TablaHash<>(grafo.getNumEstaciones() * 2 + 1);
        int numero = 0;
        ListaEnlazada<ListaEnlazada<Estacion>> subredes = encontrarSubredes();
        for (Nodo<ListaEnlazada<Estacion>> s = subredes.getPrimero(); s != null; s = s.getSiguiente()) {
            for (Nodo<Estacion> e = s.getDato().getPrimero(); e != null; e = e.getSiguiente()) {
                resultado.insertar(e.getDato().getId(), numero);
            }
            numero++;
        }
        return resultado;
    }

    /** Cantidad de conexiones de una estacion, o -1 si no existe. */
    public int contarConexiones(String id) {
        Estacion e = grafo.buscarEstacion(id);
        return e == null ? -1 : e.getGrado();
    }

    /**
     * Hubs de transferencia: las estaciones con mas conexiones, de mayor a menor.
     * Devuelve las primeras {@code cantidad}; si hay empate en el ultimo lugar
     * se incluyen todas las empatadas. Las estaciones sin conexiones no cuentan.
     */
    public Estacion[] identificarHubs(int cantidad) {
        Estacion[] todas = ordenarPorGrado();
        if (todas.length == 0 || cantidad <= 0) {
            return new Estacion[0];
        }
        int limite = Math.min(cantidad, todas.length);
        int gradoCorte = todas[limite - 1].getGrado();
        while (limite < todas.length && todas[limite].getGrado() == gradoCorte) {
            limite++; // incluir empates
        }
        // Quitar estaciones sin conexiones
        while (limite > 0 && todas[limite - 1].getGrado() == 0) {
            limite--;
        }
        Estacion[] hubs = new Estacion[limite];
        System.arraycopy(todas, 0, hubs, 0, limite);
        return hubs;
    }

    /** Estaciones ordenadas por grado descendente (insercion; empate por nombre). */
    private Estacion[] ordenarPorGrado() {
        Object[] objs = grafo.getEstaciones().aArreglo();
        Estacion[] arr = new Estacion[objs.length];
        for (int i = 0; i < objs.length; i++) {
            arr[i] = (Estacion) objs[i];
        }
        for (int i = 1; i < arr.length; i++) {
            Estacion clave = arr[i];
            int j = i - 1;
            while (j >= 0 && vaAntes(clave, arr[j])) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = clave;
        }
        return arr;
    }

    private boolean vaAntes(Estacion a, Estacion b) {
        if (a.getGrado() != b.getGrado()) {
            return a.getGrado() > b.getGrado();
        }
        return a.getNombre().compareToIgnoreCase(b.getNombre()) < 0;
    }
}
