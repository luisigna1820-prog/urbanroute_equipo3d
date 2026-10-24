package urbanroute.estructuras;

/**
 * Lista simplemente enlazada propia (no usa java.util).
 * Se recorre asi:
 * <pre>
 * for (Nodo&lt;T&gt; n = lista.getPrimero(); n != null; n = n.getSiguiente()) { ... }
 * </pre>
 *
 * @param <T> tipo de dato guardado
 */
public class ListaEnlazada<T> {

    private Nodo<T> primero;
    private Nodo<T> ultimo;
    private int tamano;

    public ListaEnlazada() {
        primero = null;
        ultimo = null;
        tamano = 0;
    }

    /** Agrega un elemento al final de la lista. O(1). */
    public void agregar(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (primero == null) {
            primero = nuevo;
        } else {
            ultimo.setSiguiente(nuevo);
        }
        ultimo = nuevo;
        tamano++;
    }

    /** Agrega un elemento al inicio de la lista. O(1). */
    public void agregarAlInicio(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        nuevo.setSiguiente(primero);
        primero = nuevo;
        if (ultimo == null) {
            ultimo = nuevo;
        }
        tamano++;
    }

    /**
     * Elimina la primera aparicion del dato (compara con equals).
     *
     * @return true si lo encontro y lo elimino
     */
    public boolean eliminar(T dato) {
        Nodo<T> anterior = null;
        Nodo<T> actual = primero;
        while (actual != null) {
            if (iguales(actual.getDato(), dato)) {
                if (anterior == null) {
                    primero = actual.getSiguiente();
                } else {
                    anterior.setSiguiente(actual.getSiguiente());
                }
                if (actual == ultimo) {
                    ultimo = anterior;
                }
                tamano--;
                return true;
            }
            anterior = actual;
            actual = actual.getSiguiente();
        }
        return false;
    }

    /** Indica si el dato esta en la lista. */
    public boolean contiene(T dato) {
        for (Nodo<T> n = primero; n != null; n = n.getSiguiente()) {
            if (iguales(n.getDato(), dato)) {
                return true;
            }
        }
        return false;
    }

    /** Devuelve el elemento en la posicion indicada (empieza en 0). O(n). */
    public T obtener(int posicion) {
        if (posicion < 0 || posicion >= tamano) {
            throw new IndexOutOfBoundsException("Posicion invalida: " + posicion);
        }
        Nodo<T> n = primero;
        for (int i = 0; i < posicion; i++) {
            n = n.getSiguiente();
        }
        return n.getDato();
    }

    /** Copia los elementos a un arreglo de Object (util para ordenar). */
    public Object[] aArreglo() {
        Object[] arreglo = new Object[tamano];
        int i = 0;
        for (Nodo<T> n = primero; n != null; n = n.getSiguiente()) {
            arreglo[i++] = n.getDato();
        }
        return arreglo;
    }

    public void vaciar() {
        primero = null;
        ultimo = null;
        tamano = 0;
    }

    public boolean estaVacia() {
        return tamano == 0;
    }

    public int getTamano() {
        return tamano;
    }

    public Nodo<T> getPrimero() {
        return primero;
    }

    private boolean iguales(T a, T b) {
        return a == null ? b == null : a.equals(b);
    }
}
