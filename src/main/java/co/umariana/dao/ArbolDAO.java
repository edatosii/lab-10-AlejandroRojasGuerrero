package co.umariana.dao;

import co.umariana.conexion.ConexionBD;
import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.Persona;
import co.umariana.modelo.ResumenArbol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO (Data Access Object): la única clase que habla con la base de datos. Guarda los árboles genealógicos
 * en las tablas "arboles" y "miembros" (ver db/schema.sql). Se construye igual que EstudianteDAO del laboratorio 9.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 4)
 * ------------------------------------------
 * 1. Cada método marcado con "TODO" lanza UnsupportedOperationException. Reemplace esa línea por su
 *    implementación con JDBC.
 * 2. NO modifique las pruebas (src/test). Las pruebas NO necesitan MySQL: usan una base de datos H2 en memoria.
 * 3. Esta etapa usa las anteriores: para guardar recorra arbol.miembros(); para cargar, reconstruya el árbol con
 *    new ArbolGenealogico(sujeto) y arbol.agregar(...).
 *
 * REGLAS (las pruebas las verifican)
 *  - Los métodos NO lanzan SQLException: la capturan, imprimen el mensaje con System.out.println y devuelven
 *    un valor "de error" (-1, false, lista vacía o null).
 *  - Use SIEMPRE PreparedStatement con parámetros (?), nunca concatene datos dentro del SQL.
 *  - Guardar un árbol son VARIAS sentencias (el árbol y todos sus miembros): hágalo en una TRANSACCIÓN
 *    (con.setAutoCommit(false); ... con.commit(); y con.rollback() si algo falla) para que nunca quede
 *    guardado "a medias". Antes de cerrar la conexión vuelva a dejar setAutoCommit(true).
 *  - El id de cada fila lo genera la base de datos (AUTO_INCREMENT).
 *  - Los miembros se guardan en el orden de arbol.miembros(): así, al leerlos ORDER BY id, cada persona
 *    aparece después de la que necesita para poder agregarla al árbol.
 */
public class ArbolDAO {

    // =====================================================================
    // ETAPA 4.1. Guardar un árbol nuevo (INSERT en dos tablas, con transacción)
    // =====================================================================

    /**
     * Guarda un árbol nuevo: una fila en "arboles" (con el nombre) y una fila en "miembros" por cada persona.
     *
     * @return el id que la base de datos le dio al árbol; -1 si el nombre es nulo, está en blanco o tiene más
     *         de 100 caracteres, si el árbol es null, o si hubo un error (en ese caso NO queda nada guardado)
     */
    public long guardar(String nombre, ArbolGenealogico arbol) {
if (nombre == null || nombre.isBlank() || nombre.length() > 100 || arbol == null) {
            return -1;
        }

        String sqlArbol = "INSERT INTO arboles (nombre) VALUES (?)";
        String sqlMiembro = "INSERT INTO miembros (arbol_id, posicion, nombre, nacimiento, fallecimiento) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = ConexionBD.conectar()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            con.setAutoCommit(false);

            try {
                long arbolId = -1;
                try (PreparedStatement psArbol = con.prepareStatement(sqlArbol, Statement.RETURN_GENERATED_KEYS)) {
                    psArbol.setString(1, nombre);
                    psArbol.executeUpdate();
                    try (ResultSet rs = psArbol.getGeneratedKeys()) {
                        if (rs.next()) {
                            arbolId = rs.getLong(1);
                        }
                    }
                }

                if (arbolId == -1) {
                    con.rollback();
                    return -1;
                }

                try (PreparedStatement psMiembro = con.prepareStatement(sqlMiembro)) {
                    for (Persona p : arbol.miembros()) {
                        psMiembro.setLong(1, arbolId);
                        psMiembro.setString(2, p.getPosicion());
                        psMiembro.setString(3, p.getNombre());
                        psMiembro.setInt(4, p.getNacimiento());
                        if (p.getFallecimiento() == null) {
                            psMiembro.setNull(5, java.sql.Types.INTEGER);
                        } else {
                            psMiembro.setInt(5, p.getFallecimiento());
                        }
                        psMiembro.executeUpdate();
                    }
                }

                con.commit();
                return arbolId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(autoCommitPrevio);
            }
        } catch (SQLException e) {
            System.out.println("Error al guardar el árbol: " + e.getMessage());
            return -1;
        }
    }

    // =====================================================================
    // ETAPA 4.2. Listar (SELECT con JOIN y COUNT)
    // =====================================================================

    /**
     * @return un resumen por cada árbol guardado (id, nombre, cuántos miembros tiene y cuándo se creó),
     *         ordenados por id; lista vacía si no hay árboles o si hay un error
     */
    public List<ResumenArbol> listar() {
List<ResumenArbol> lista = new ArrayList<>();
        String sql = "SELECT a.id, a.nombre, a.creado, COUNT(m.id) AS total "
                + "FROM arboles a LEFT JOIN miembros m ON m.arbol_id = a.id "
                + "GROUP BY a.id, a.nombre, a.creado ORDER BY a.id";

        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String nombre = rs.getString("nombre");
                LocalDateTime creado = rs.getTimestamp("creado").toLocalDateTime();
                int total = rs.getInt("total");
                lista.add(new ResumenArbol(id, nombre, total, creado));
            }
        } catch (SQLException e) {
            System.out.println("Error al listar los árboles: " + e.getMessage());
            return new ArrayList<>();
        }
        return lista;
    }

    // =====================================================================
    // ETAPA 4.3. Cargar un árbol (SELECT y reconstrucción del árbol)
    // =====================================================================

    /**
     * @return el árbol con ese id, reconstruido con todos sus miembros; null si no existe ese id o si hay un error
     */
    public ArbolGenealogico cargar(long id) {
       String sql = "SELECT posicion, nombre, nacimiento, fallecimiento FROM miembros WHERE arbol_id = ? ORDER BY id";

        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                ArbolGenealogico arbol = null;
                while (rs.next()) {
                    String pos = rs.getString("posicion");
                    String nom = rs.getString("nombre");
                    int nac = rs.getInt("nacimiento");
                    int falVal = rs.getInt("fallecimiento");
                    Integer fal = rs.wasNull() ? null : falVal;

                    Persona p = new Persona(pos, nom, nac, fal);
                    if (arbol == null) {
                        arbol = new ArbolGenealogico(p);
                    } else {
                        arbol.agregar(p);
                    }
                }
                return arbol;
            }
        } catch (SQLException e) {
            System.out.println("Error al cargar el árbol: " + e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // ETAPA 4.4. Actualizar (reemplazar los miembros de un árbol, con transacción)
    // =====================================================================

    /**
     * Guarda los cambios de un árbol ya existente (por ejemplo, después de agregar o eliminar miembros):
     * deja en la base de datos exactamente los miembros de {@code arbol}. El nombre del árbol NO cambia.
     *
     * @return true si se actualizó; false si no existe un árbol con ese id, si el árbol es null o si hubo un
     *         error (en ese caso NO se modifica nada: ni se borran los miembros anteriores)
     */
    public boolean actualizar(long id, ArbolGenealogico arbol) {
        if (arbol == null) {
            return false;
        }

        String sqlExiste = "SELECT id FROM arboles WHERE id = ?";
        String sqlDelete = "DELETE FROM miembros WHERE arbol_id = ?";
        String sqlInsert = "INSERT INTO miembros (arbol_id, posicion, nombre, nacimiento, fallecimiento) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = ConexionBD.conectar()) {
            boolean autoCommitPrevio = con.getAutoCommit();
            con.setAutoCommit(false);

            try {
                try (PreparedStatement psExiste = con.prepareStatement(sqlExiste)) {
                    psExiste.setLong(1, id);
                    try (ResultSet rs = psExiste.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return false;
                        }
                    }
                }

                try (PreparedStatement psDelete = con.prepareStatement(sqlDelete)) {
                    psDelete.setLong(1, id);
                    psDelete.executeUpdate();
                }

                try (PreparedStatement psInsert = con.prepareStatement(sqlInsert)) {
                    for (Persona p : arbol.miembros()) {
                        psInsert.setLong(1, id);
                        psInsert.setString(2, p.getPosicion());
                        psInsert.setString(3, p.getNombre());
                        psInsert.setInt(4, p.getNacimiento());
                        if (p.getFallecimiento() == null) {
                            psInsert.setNull(5, java.sql.Types.INTEGER);
                        } else {
                            psInsert.setInt(5, p.getFallecimiento());
                        }
                        psInsert.executeUpdate();
                    }
                }

                con.commit();
                return true;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(autoCommitPrevio);
            }
        } catch (SQLException e) {
            System.out.println("Error al actualizar el árbol: " + e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // ETAPA 4.5. Eliminar (DELETE)
    // =====================================================================

    /**
     * Elimina el árbol y todos sus miembros (la base de datos borra los miembros sola: ON DELETE CASCADE).
     *
     * @return true si se eliminó; false si no existía ese id o si hubo un error
     */
    public boolean eliminar(long id) {
String sql = "DELETE FROM arboles WHERE id = ?";
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            int filasAfectadas = ps.executeUpdate();
            return filasAfectadas > 0;
        } catch (SQLException e) {
            System.out.println("Error al eliminar el árbol: " + e.getMessage());
            return false;
        }
    }
}