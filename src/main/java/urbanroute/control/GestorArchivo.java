package urbanroute.control;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import urbanroute.estructuras.ListaEnlazada;
import urbanroute.estructuras.Nodo;
import urbanroute.modelo.Conexion;
import urbanroute.modelo.Estacion;
import urbanroute.modelo.Grafo;

/**
 * Lee y guarda los archivos CSV de la red.
 * <ul>
 *   <li>diccionario.csv: id, nombre, linea, zona, capacidad</li>
 *   <li>grafo.csv: origen, destino, tiempo (minutos)</li>
 * </ul>
 * Acepta coma o punto y coma como separador, con o sin encabezado. Si hay
 * encabezado, las columnas se reconocen por su nombre, en cualquier orden.
 * Las lineas con errores no detienen la carga: se saltan y se reportan.
 */
public class GestorArchivo {

    public static final String NOMBRE_DICCIONARIO = "diccionario.csv";
    public static final String NOMBRE_GRAFO = "grafo.csv";

    private File archivoDiccionario;
    private File archivoGrafo;
    private ListaEnlazada<String> advertencias = new ListaEnlazada<>();

    // ------------------------------------------------------------------
    // Carga
    // ------------------------------------------------------------------

    /**
     * Carga los dos archivos en un grafo NUEVO. Si algo falla de forma grave
     * (archivo ilegible o sin estaciones validas) lanza IOException y el grafo
     * anterior no se toca.
     */
    public Grafo cargar(File diccionario, File grafoCsv) throws IOException {
        advertencias = new ListaEnlazada<>();
        Grafo g = new Grafo();
        leerDiccionario(diccionario, g);
        if (g.estaVacio()) {
            throw new IOException("El archivo " + diccionario.getName() + " no tiene estaciones validas.");
        }
        if (grafoCsv != null && grafoCsv.exists()) {
            leerConexiones(grafoCsv, g);
        } else {
            advertencias.agregar("No se encontro el archivo de conexiones; la red queda sin tramos.");
        }
        archivoDiccionario = diccionario;
        archivoGrafo = grafoCsv != null ? grafoCsv : new File(diccionario.getParentFile(), NOMBRE_GRAFO);
        return g;
    }

    /** Carga el archivo maestro (carpeta data/) al iniciar el programa. */
    public Grafo cargarMaestro(File carpeta) throws IOException {
        File dic = new File(carpeta, NOMBRE_DICCIONARIO);
        if (!dic.exists()) {
            throw new IOException("No existe " + dic.getPath());
        }
        return cargar(dic, new File(carpeta, NOMBRE_GRAFO));
    }

    /** Lee y valida diccionario.csv. */
    private void leerDiccionario(File archivo, Grafo g) throws IOException {
        String[] lineas = leerLineas(archivo);
        if (lineas.length == 0) {
            throw new IOException("El archivo " + archivo.getName() + " esta vacio.");
        }
        char sep = detectarSeparador(lineas[0]);
        // Posiciones por defecto (sin encabezado)
        int cId = 0, cNombre = 1, cLinea = 2, cZona = 3, cCap = 4;
        int inicio = 0;
        String[] primera = dividir(lineas[0], sep);
        if (esEncabezado(primera)) {
            inicio = 1;
            cId = buscarColumna(primera, -1, "id", "codigo", "cod");
            cNombre = buscarColumna(primera, -1, "nombre", "estacion", "name");
            cLinea = buscarColumna(primera, -1, "linea", "line", "ruta");
            cZona = buscarColumna(primera, -1, "zona", "sector", "municipio", "parroquia");
            cCap = buscarColumna(primera, -1, "capacidad", "pasajeros", "pax");
            if (cId == -1) {
                cId = 0;
            }
            if (cNombre == -1) {
                cNombre = 1;
            }
        }

        for (int i = inicio; i < lineas.length; i++) {
            int numLinea = i + 1;
            if (lineas[i].trim().isEmpty()) {
                continue;
            }
            String[] c = dividir(lineas[i], sep);
            String id = campo(c, cId);
            String nombre = campo(c, cNombre);
            if (id.isEmpty() || nombre.isEmpty()) {
                advertencias.agregar(archivo.getName() + " linea " + numLinea + ": falta el ID o el nombre. Se omitio.");
                continue;
            }
            if (g.existeEstacion(id)) {
                advertencias.agregar(archivo.getName() + " linea " + numLinea + ": ID repetido '" + id + "'. Se omitio.");
                continue;
            }
            int capacidad = 0;
            String txtCap = campo(c, cCap);
            if (!txtCap.isEmpty()) {
                try {
                    capacidad = (int) Math.round(Double.parseDouble(txtCap.replace(",", ".")));
                    if (capacidad < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    advertencias.agregar(archivo.getName() + " linea " + numLinea + ": capacidad invalida '" + txtCap + "', se uso 0.");
                    capacidad = 0;
                }
            }
            g.agregarEstacion(new Estacion(id, nombre, campo(c, cLinea), campo(c, cZona), capacidad));
        }
    }

    /** Lee y valida grafo.csv. */
    private void leerConexiones(File archivo, Grafo g) throws IOException {
        String[] lineas = leerLineas(archivo);
        if (lineas.length == 0) {
            advertencias.agregar("El archivo " + archivo.getName() + " esta vacio.");
            return;
        }
        char sep = detectarSeparador(lineas[0]);
        int cOrigen = 0, cDestino = 1, cTiempo = 2;
        int inicio = 0;
        String[] primera = dividir(lineas[0], sep);
        if (esEncabezado(primera)) {
            inicio = 1;
            cOrigen = buscarColumna(primera, 0, "origen", "desde", "from", "id_origen", "estacion1");
            cDestino = buscarColumna(primera, 1, "destino", "hasta", "to", "id_destino", "estacion2");
            cTiempo = buscarColumna(primera, 2, "tiempo", "minutos", "min", "peso", "duracion");
        }

        for (int i = inicio; i < lineas.length; i++) {
            int numLinea = i + 1;
            if (lineas[i].trim().isEmpty()) {
                continue;
            }
            String[] c = dividir(lineas[i], sep);
            String o = campo(c, cOrigen);
            String d = campo(c, cDestino);
            String t = campo(c, cTiempo);
            String donde = archivo.getName() + " linea " + numLinea + ": ";
            if (o.isEmpty() || d.isEmpty() || t.isEmpty()) {
                advertencias.agregar(donde + "faltan datos. Se omitio.");
                continue;
            }
            if (!g.existeEstacion(o) || !g.existeEstacion(d)) {
                advertencias.agregar(donde + "la estacion '" + (!g.existeEstacion(o) ? o : d) + "' no existe en el diccionario. Se omitio.");
                continue;
            }
            double tiempo;
            try {
                tiempo = Double.parseDouble(t.replace(",", "."));
            } catch (NumberFormatException ex) {
                advertencias.agregar(donde + "tiempo no numerico '" + t + "'. Se omitio.");
                continue;
            }
            if (tiempo <= 0) {
                advertencias.agregar(donde + "el tiempo debe ser mayor que 0. Se omitio.");
                continue;
            }
            if (o.equalsIgnoreCase(d)) {
                advertencias.agregar(donde + "una estacion no se puede conectar consigo misma. Se omitio.");
                continue;
            }
            if (!g.agregarConexion(o, d, tiempo)) {
                advertencias.agregar(donde + "conexion repetida " + o + " - " + d + ". Se omitio.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Guardado
    // ------------------------------------------------------------------

    /** Indica si hay archivos asociados donde guardar. */
    public boolean tieneArchivos() {
        return archivoDiccionario != null;
    }

    /** Fija la carpeta donde se guardaran los archivos (cuando no hay ninguno cargado). */
    public void setCarpetaDestino(File carpeta) {
        archivoDiccionario = new File(carpeta, NOMBRE_DICCIONARIO);
        archivoGrafo = new File(carpeta, NOMBRE_GRAFO);
    }

    /** Sobrescribe los archivos fuente con el estado actual de la red (altas y bajas). */
    public void guardar(Grafo g) throws IOException {
        if (archivoDiccionario == null) {
            throw new IOException("No hay archivo asociado. Seleccione una carpeta primero.");
        }
        StringBuilder dic = new StringBuilder("id,nombre,linea,zona,capacidad\n");
        StringBuilder gra = new StringBuilder("origen,destino,tiempo\n");
        for (Nodo<Estacion> n = g.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion e = n.getDato();
            dic.append(csv(e.getId())).append(',').append(csv(e.getNombre())).append(',')
               .append(csv(e.getLinea())).append(',').append(csv(e.getZona())).append(',')
               .append(e.getCapacidad()).append('\n');
        }
        // Cada tramo se escribe una sola vez: cuando el origen aparece antes que el destino
        ListaEnlazada<Estacion> yaEscritas = new ListaEnlazada<>();
        for (Nodo<Estacion> n = g.getEstaciones().getPrimero(); n != null; n = n.getSiguiente()) {
            Estacion e = n.getDato();
            for (Nodo<Conexion> c = g.getVecinos(e.getId()).getPrimero(); c != null; c = c.getSiguiente()) {
                Estacion destino = c.getDato().getDestino();
                if (!yaEscritas.contiene(destino)) {
                    gra.append(csv(e.getId())).append(',').append(csv(destino.getId())).append(',')
                       .append(CalculadorRutas.formatear(c.getDato().getTiempo()).replace(",", ".")).append('\n');
                }
            }
            yaEscritas.agregar(e);
        }
        escribir(archivoDiccionario, dic.toString());
        escribir(archivoGrafo, gra.toString());
    }

    // ------------------------------------------------------------------
    // Utilidades CSV
    // ------------------------------------------------------------------

    private String[] leerLineas(File archivo) throws IOException {
        ListaEnlazada<String> lista = new ListaEnlazada<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {
            String linea;
            boolean primera = true;
            while ((linea = br.readLine()) != null) {
                if (primera && linea.startsWith("﻿")) {
                    linea = linea.substring(1); // quitar BOM de Excel
                }
                primera = false;
                lista.agregar(linea);
            }
        }
        Object[] objs = lista.aArreglo();
        String[] res = new String[objs.length];
        for (int i = 0; i < objs.length; i++) {
            res[i] = (String) objs[i];
        }
        return res;
    }

    private void escribir(File archivo, String contenido) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
            bw.write(contenido);
        }
    }

    private char detectarSeparador(String linea) {
        int comas = 0, puntoComa = 0;
        for (int i = 0; i < linea.length(); i++) {
            if (linea.charAt(i) == ',') {
                comas++;
            } else if (linea.charAt(i) == ';') {
                puntoComa++;
            }
        }
        return puntoComa > comas ? ';' : ',';
    }

    /** Divide una linea CSV respetando campos entre comillas. */
    private String[] dividir(String linea, char sep) {
        ListaEnlazada<String> campos = new ListaEnlazada<>();
        StringBuilder actual = new StringBuilder();
        boolean enComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char ch = linea.charAt(i);
            if (ch == '"') {
                if (enComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else {
                    enComillas = !enComillas;
                }
            } else if (ch == sep && !enComillas) {
                campos.agregar(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(ch);
            }
        }
        campos.agregar(actual.toString().trim());
        Object[] objs = campos.aArreglo();
        String[] res = new String[objs.length];
        for (int i = 0; i < objs.length; i++) {
            res[i] = (String) objs[i];
        }
        return res;
    }

    /** La primera fila es encabezado si contiene palabras conocidas o no tiene numeros. */
    private boolean esEncabezado(String[] fila) {
        for (String f : fila) {
            String x = quitarAcentos(f.toLowerCase());
            if (x.equals("id") || x.contains("nombre") || x.contains("origen") || x.contains("destino")
                    || x.contains("tiempo") || x.contains("linea") || x.contains("capacidad")) {
                return true;
            }
        }
        return false;
    }

    private int buscarColumna(String[] encabezado, int porDefecto, String... nombres) {
        for (String nombre : nombres) {
            for (int i = 0; i < encabezado.length; i++) {
                String h = quitarAcentos(encabezado[i].toLowerCase().trim());
                if (h.equals(nombre)) {
                    return i;
                }
            }
        }
        for (String nombre : nombres) {
            for (int i = 0; i < encabezado.length; i++) {
                String h = quitarAcentos(encabezado[i].toLowerCase().trim());
                if (nombre.length() > 2 && h.contains(nombre)) {
                    return i;
                }
            }
        }
        return porDefecto;
    }

    private String campo(String[] c, int i) {
        return (i >= 0 && i < c.length) ? c[i].trim() : "";
    }

    private String csv(String s) {
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String quitarAcentos(String s) {
        return s.replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u');
    }

    // ------------------------------------------------------------------

    public ListaEnlazada<String> getAdvertencias() {
        return advertencias;
    }

    public File getArchivoDiccionario() {
        return archivoDiccionario;
    }

    public File getArchivoGrafo() {
        return archivoGrafo;
    }
}
