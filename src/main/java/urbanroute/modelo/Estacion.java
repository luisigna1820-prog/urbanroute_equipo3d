package urbanroute.modelo;

import urbanroute.estructuras.ListaEnlazada;

/**
 * Datos de una estacion (nodo del grafo).
 * La lista de conexiones la administra unicamente la clase Grafo.
 */
public class Estacion {

    private final String id;
    private String nombre;
    private String linea;
    private String zona;
    private int capacidad; // pasajeros por hora

    /** Lista de adyacencia de esta estacion (la maneja el Grafo). */
    private final ListaEnlazada<Conexion> conexiones;

    public Estacion(String id, String nombre, String linea, String zona, int capacidad) {
        this.id = id.trim();
        this.nombre = nombre.trim();
        this.linea = linea == null ? "" : linea.trim();
        this.zona = zona == null ? "" : zona.trim();
        this.capacidad = capacidad;
        this.conexiones = new ListaEnlazada<>();
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getLinea() {
        return linea;
    }

    public void setLinea(String linea) {
        this.linea = linea;
    }

    public String getZona() {
        return zona;
    }

    public void setZona(String zona) {
        this.zona = zona;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }

    /** Solo el Grafo (mismo paquete) modifica la lista de adyacencia. */
    ListaEnlazada<Conexion> getConexionesInterno() {
        return conexiones;
    }

    /** Numero de conexiones de la estacion (grado del nodo). */
    public int getGrado() {
        return conexiones.getTamano();
    }

    /** Texto con toda la informacion de la estacion. */
    public String getInformacion() {
        return "ID: " + id
                + "\nNombre: " + nombre
                + "\nLinea: " + (linea.isEmpty() ? "-" : linea)
                + "\nZona: " + (zona.isEmpty() ? "-" : zona)
                + "\nCapacidad: " + capacidad + " pasajeros/hora"
                + "\nConexiones: " + getGrado();
    }

    @Override
    public String toString() {
        return nombre + " (" + id + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Estacion)) {
            return false;
        }
        return id.equalsIgnoreCase(((Estacion) o).id);
    }

    @Override
    public int hashCode() {
        return id.toUpperCase().hashCode();
    }
}
