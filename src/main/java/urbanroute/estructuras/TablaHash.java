package urbanroute.estructuras;

/**
 * Tabla hash propia con clave String (el ID de la estacion).
 * <p>
 * Colisiones: encadenamiento separado (cada casilla es una cadena de nodos).
 * Cuando el factor de carga pasa de 0.75 la tabla duplica su tamano (rehash),
 * asi las busquedas se mantienen en O(1) promedio.
 *
 * @param <V> tipo del valor guardado
 */
public class TablaHash<V> {

    private static final int CAPACIDAD_INICIAL = 31;
    private static final double FACTOR_CARGA_MAX = 0.75;

    /** Nodo de una cadena de colisiones. */
    private static class Entrada<V> {
        final String clave;
        V valor;
        Entrada<V> siguiente;

        Entrada(String clave, V valor, Entrada<V> siguiente) {
            this.clave = clave;
            this.valor = valor;
            this.siguiente = siguiente;
        }
    }

    private Entrada<V>[] casillas;
    private int tamano;

    public TablaHash() {
        this(CAPACIDAD_INICIAL);
    }

    @SuppressWarnings("unchecked")
    public TablaHash(int capacidad) {
        casillas = (Entrada<V>[]) new Entrada[Math.max(capacidad, 7)];
        tamano = 0;
    }

    /**
     * Funcion hash propia (polinomial, base 31) sobre los caracteres de la clave.
     * Se ignoran mayusculas/minusculas y espacios a los lados para que
     * "L1-01" y " l1-01" sean la misma estacion.
     */
    private int indice(String clave, int capacidad) {
        String k = normalizar(clave);
        long h = 0;
        for (int i = 0; i < k.length(); i++) {
            h = (h * 31 + k.charAt(i)) % 1_000_000_007L;
        }
        return (int) (h % capacidad);
    }

    private static String normalizar(String clave) {
        return clave == null ? "" : clave.trim().toUpperCase();
    }

    /**
     * Inserta o reemplaza el valor de una clave.
     *
     * @return true si la clave era nueva, false si se reemplazo
     */
    public boolean insertar(String clave, V valor) {
        String k = normalizar(clave);
        int i = indice(k, casillas.length);
        for (Entrada<V> e = casillas[i]; e != null; e = e.siguiente) {
            if (e.clave.equals(k)) {
                e.valor = valor;
                return false;
            }
        }
        casillas[i] = new Entrada<>(k, valor, casillas[i]);
        tamano++;
        if ((double) tamano / casillas.length > FACTOR_CARGA_MAX) {
            redimensionar();
        }
        return true;
    }

    /** Busca el valor de una clave. Devuelve null si no existe. O(1) promedio. */
    public V buscar(String clave) {
        String k = normalizar(clave);
        int i = indice(k, casillas.length);
        for (Entrada<V> e = casillas[i]; e != null; e = e.siguiente) {
            if (e.clave.equals(k)) {
                return e.valor;
            }
        }
        return null;
    }

    public boolean contiene(String clave) {
        String k = normalizar(clave);
        int i = indice(k, casillas.length);
        for (Entrada<V> e = casillas[i]; e != null; e = e.siguiente) {
            if (e.clave.equals(k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Elimina una clave.
     *
     * @return el valor eliminado o null si no existia
     */
    public V eliminar(String clave) {
        String k = normalizar(clave);
        int i = indice(k, casillas.length);
        Entrada<V> anterior = null;
        for (Entrada<V> e = casillas[i]; e != null; e = e.siguiente) {
            if (e.clave.equals(k)) {
                if (anterior == null) {
                    casillas[i] = e.siguiente;
                } else {
                    anterior.siguiente = e.siguiente;
                }
                tamano--;
                return e.valor;
            }
            anterior = e;
        }
        return null;
    }

    /** Deja la tabla vacia (se usa al cargar un archivo nuevo). */
    @SuppressWarnings("unchecked")
    public void vaciar() {
        casillas = (Entrada<V>[]) new Entrada[CAPACIDAD_INICIAL];
        tamano = 0;
    }

    /** Devuelve todos los valores guardados (sin orden particular). */
    public ListaEnlazada<V> valores() {
        ListaEnlazada<V> lista = new ListaEnlazada<>();
        for (Entrada<V> casilla : casillas) {
            for (Entrada<V> e = casilla; e != null; e = e.siguiente) {
                lista.agregar(e.valor);
            }
        }
        return lista;
    }

    public int getTamano() {
        return tamano;
    }

    public boolean estaVacia() {
        return tamano == 0;
    }

    /** Duplica la capacidad (al siguiente impar) y reubica todas las entradas. */
    @SuppressWarnings("unchecked")
    private void redimensionar() {
        Entrada<V>[] viejas = casillas;
        casillas = (Entrada<V>[]) new Entrada[viejas.length * 2 + 1];
        for (Entrada<V> casilla : viejas) {
            Entrada<V> e = casilla;
            while (e != null) {
                Entrada<V> sig = e.siguiente;
                int i = indice(e.clave, casillas.length);
                e.siguiente = casillas[i];
                casillas[i] = e;
                e = sig;
            }
        }
    }
}
