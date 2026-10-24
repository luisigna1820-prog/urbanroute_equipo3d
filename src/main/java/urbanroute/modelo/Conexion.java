package urbanroute.modelo;

/**
 * Tramo hacia otra estacion (arista del grafo) con su tiempo en minutos.
 */
public class Conexion {

    private final Estacion destino;
    private double tiempo;

    public Conexion(Estacion destino, double tiempo) {
        this.destino = destino;
        this.tiempo = tiempo;
    }

    public Estacion getDestino() {
        return destino;
    }

    public double getTiempo() {
        return tiempo;
    }

    public void setTiempo(double tiempo) {
        this.tiempo = tiempo;
    }

    /** Dos conexiones son iguales si van a la misma estacion. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Conexion)) {
            return false;
        }
        return destino.equals(((Conexion) o).destino);
    }

    @Override
    public int hashCode() {
        return destino.hashCode();
    }

    @Override
    public String toString() {
        return "-> " + destino.getNombre() + " (" + tiempo + " min)";
    }
}
