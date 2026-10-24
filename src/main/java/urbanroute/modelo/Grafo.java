package urbanroute.modelo;

import urbanroute.estructuras.ListaEnlazada;
import urbanroute.estructuras.Nodo;
import urbanroute.estructuras.TablaHash;

/**
 * Red de transporte: grafo no dirigido y ponderado, guardado con listas de
 * adyacencia. Las estaciones se buscan siempre en la TablaHash (O(1)), nunca
 * recorriendo toda la red.
 */
public class Grafo {

    /** Acceso directo a cada estacion por su ID. */
    private final TablaHash<Estacion> tabla;
    /** Estaciones en orden de insercion (para dibujar y guardar en orden). */
    private final ListaEnlazada<Estacion> estaciones;
    private int numConexiones;

    public Grafo() {
        tabla = new TablaHash<>();
        estaciones = new ListaEnlazada<>();
        numConexiones = 0;
    }

    // ------------------------------------------------------------------
    // Estaciones
    // ------------------------------------------------------------------

    /**
     * Agrega una estacion nueva.
     *
     * @return false si ya existe una estacion con ese ID
     */
    public boolean agregarEstacion(Estacion e) {
        if (e == null || tabla.contiene(e.getId())) {
            return false;
        }
        tabla.insertar(e.getId(), e);
        estaciones.agregar(e);
        return true;
    }

    /**
     * Elimina una estacion y todas las conexiones que llegan o salen de ella.
     *
     * @return la estacion eliminada o null si no existia
     */
    public Estacion eliminarEstacion(String id) {
        Estacion e = tabla.buscar(id);
        if (e == null) {
            return null;
        }
        // Borrar la conexion inversa en cada vecino
        for (Nodo<Conexion> n = e.getConexionesInterno().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion vecino = n.getDato().getDestino();
            vecino.getConexionesInterno().eliminar(new Conexion(e, 0));
            numConexiones--;
        }
        e.getConexionesInterno().vaciar();
        tabla.eliminar(id);
        estaciones.eliminar(e);
        return e;
    }

    /** Busca una estacion por su ID (delegado a la TablaHash). */
    public Estacion buscarEstacion(String id) {
        return tabla.buscar(id);
    }

    public boolean existeEstacion(String id) {
        return tabla.contiene(id);
    }

    // ------------------------------------------------------------------
    // Conexiones
    // ------------------------------------------------------------------

    /**
     * Agrega una conexion en ambos sentidos.
     *
     * @return false si alguna estacion no existe, es la misma estacion,
     *         el tiempo no es positivo o la conexion ya existe
     */
    public boolean agregarConexion(String idOrigen, String idDestino, double tiempo) {
        Estacion a = tabla.buscar(idOrigen);
        Estacion b = tabla.buscar(idDestino);
        if (a == null || b == null || a == b || tiempo <= 0) {
            return false;
        }
        if (existeConexion(idOrigen, idDestino)) {
            return false;
        }
        a.getConexionesInterno().agregar(new Conexion(b, tiempo));
        b.getConexionesInterno().agregar(new Conexion(a, tiempo));
        numConexiones++;
        return true;
    }

    /**
     * Elimina la conexion entre dos estaciones (en ambos sentidos).
     *
     * @return false si no existia
     */
    public boolean eliminarConexion(String idOrigen, String idDestino) {
        Estacion a = tabla.buscar(idOrigen);
        Estacion b = tabla.buscar(idDestino);
        if (a == null || b == null) {
            return false;
        }
        boolean ok1 = a.getConexionesInterno().eliminar(new Conexion(b, 0));
        boolean ok2 = b.getConexionesInterno().eliminar(new Conexion(a, 0));
        if (ok1 || ok2) {
            numConexiones--;
            return true;
        }
        return false;
    }

    public boolean existeConexion(String idOrigen, String idDestino) {
        Estacion a = tabla.buscar(idOrigen);
        Estacion b = tabla.buscar(idDestino);
        if (a == null || b == null) {
            return false;
        }
        return a.getConexionesInterno().contiene(new Conexion(b, 0));
    }

    /** Tiempo de la conexion directa entre dos estaciones, o -1 si no existe. */
    public double getTiempo(String idOrigen, String idDestino) {
        Estacion a = tabla.buscar(idOrigen);
        Estacion b = tabla.buscar(idDestino);
        if (a == null || b == null) {
            return -1;
        }
        for (Nodo<Conexion> n = a.getConexionesInterno().getPrimero(); n != null; n = n.getSiguiente()) {
            if (n.getDato().getDestino() == b) {
                return n.getDato().getTiempo();
            }
        }
        return -1;
    }

    /** Vecinos (conexiones salientes) de una estacion. Lista vacia si no existe. */
    public ListaEnlazada<Conexion> getVecinos(String id) {
        Estacion e = tabla.buscar(id);
        return e == null ? new ListaEnlazada<>() : e.getConexionesInterno();
    }

    // ------------------------------------------------------------------
    // Consultas generales
    // ------------------------------------------------------------------

    public ListaEnlazada<Estacion> getEstaciones() {
        return estaciones;
    }

    public TablaHash<Estacion> getTabla() {
        return tabla;
    }

    public int getNumEstaciones() {
        return estaciones.getTamano();
    }

    public int getNumConexiones() {
        return numConexiones;
    }

    public boolean estaVacio() {
        return estaciones.estaVacia();
    }

    /** Borra toda la red (se usa al cargar un archivo nuevo). */
    public void vaciar() {
        for (Nodo<Estacion> n = estaciones.getPrimero(); n != null; n = n.getSiguiente()) {
            n.getDato().getConexionesInterno().vaciar();
        }
        estaciones.vaciar();
        tabla.vaciar();
        numConexiones = 0;
    }
}
