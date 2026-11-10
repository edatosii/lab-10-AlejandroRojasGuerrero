package co.umariana.modelo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * El árbol genealógico: un SUJETO (posición "1") con sus ASCENDIENTES (padres, abuelos... en Ahnentafel)
 * y sus DESCENDIENTES (hijos, nietos... en d'Aboville). Ver la clase Posicion.
 *
 * Son en realidad dos árboles unidos en la raíz:
 *
 *     ascendientes:  árbol BINARIO. Cada persona tiene como mucho un padre (2n) y una madre (2n + 1).
 *     descendientes: árbol GENERAL. Cada persona tiene una lista de hijos (1.1, 1.2, ...) ordenada por número.
 *
 * El árbol solo guarda a los miembros que EXISTEN: no hay "huecos" ni personas vacías. Puede faltar el padre
 * de alguien, o el hijo 1.1 aunque exista el 1.2.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 2)
 * ------------------------------------------
 * 1. Implemente el árbol con sus PROPIOS NODOS: una clase interna "Nodo" con la Persona, el nodo del
 *    padre, el nodo de la madre y la lista de hijos. NO se permite usar Map ni Set (las pruebas lo
 *    revisan): para buscar una posición recorra el árbol siguiendo su camino
 *      - Ahnentafel: los bits de n después del primer 1 dicen el camino (0 = padre, 1 = madre).
 *        Ej.: 5 = 101 -> desde el sujeto: padre (0), luego madre (1).
 *      - D'Aboville: cada número de la posición dice a qué hijo bajar. Ej.: 1.2.3 -> hijo 2, luego su hijo 3.
 *    Se permite usar List/ArrayList para la lista de hijos.
 * 2. Todos los errores de las reglas se informan con {@link ArbolInvalidoException} (un mensaje claro).
 *    Los datos inválidos de una Persona los detecta Persona.validar() (IllegalArgumentException).
 * 3. NO modifique las pruebas (src/test).
 *
 * REGLAS DEL ÁRBOL
 *   - La posición "1" (el sujeto) existe siempre y no se puede eliminar.
 *   - Una posición no puede repetirse.
 *   - Un ascendiente n solo se puede agregar si ya existe su hijo n / 2, y debe haber NACIDO ANTES
 *     (año de nacimiento menor) que ese hijo.
 *   - Un descendiente solo se puede agregar si ya existe su progenitor, y debe haber NACIDO DESPUÉS
 *     (año de nacimiento mayor) que su progenitor.
 */
public class ArbolGenealogico {

    private static class Nodo {
        Persona persona;
        Nodo padre;
        Nodo madre;
        List<Nodo> hijos = new ArrayList<>();

        Nodo(Persona persona) {
            this.persona = persona;
        }
    }

    private final Nodo raiz;
    private int tamano;

    public ArbolGenealogico(Persona sujeto) {
        if (sujeto == null) {
            throw new IllegalArgumentException("El sujeto no puede ser nulo.");
        }
        sujeto.validar();
        if (!Posicion.esSujeto(sujeto.getPosicion())) {
            throw new ArbolInvalidoException("La posición del sujeto debe ser 1.");
        }
        this.raiz = new Nodo(sujeto);
        this.tamano = 1;
    }

    private Nodo buscarNodo(String posicion) {
        if (posicion == null || !Posicion.esValida(posicion)) {
            return null;
        }
        if (Posicion.esSujeto(posicion)) {
            return raiz;
        }
        if (Posicion.esAhnentafel(posicion)) {
            int n = Integer.parseInt(posicion);
            String binario = Integer.toBinaryString(n);
            Nodo actual = raiz;
            for (int i = 1; i < binario.length(); i++) {
                if (actual == null) {
                    return null;
                }
                char bit = binario.charAt(i);
                if (bit == '0') {
                    actual = actual.padre;
                } else {
                    actual = actual.madre;
                }
            }
            return actual;
        } else {
            String[] partes = posicion.split("\\.");
            Nodo actual = raiz;
            StringBuilder prefijo = new StringBuilder("1");
            for (int i = 1; i < partes.length; i++) {
                if (actual == null) {
                    return null;
                }
                prefijo.append(".").append(partes[i]);
                String posBuscada = prefijo.toString();
                Nodo siguiente = null;
                for (Nodo hijo : actual.hijos) {
                    if (hijo.persona.getPosicion().equals(posBuscada)) {
                        siguiente = hijo;
                        break;
                    }
                }
                actual = siguiente;
            }
            return actual;
        }
    }

    public Persona sujeto() {
        return raiz.persona;
    }

    public int tamano() {
        return tamano;
    }

    public Persona buscar(String posicion) {
        Nodo n = buscarNodo(posicion);
        return n != null ? n.persona : null;
    }

    public boolean contiene(String posicion) {
        return buscarNodo(posicion) != null;
    }

    /**
     * Agrega una persona en la posición que ella misma trae (la usa el constructor del árbol a partir del texto).
     * El orden de llamada importa: primero debe existir la persona de la que depende (ver REGLAS DEL ÁRBOL).
     *
     * @return la misma persona
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si la posición ya existe (o es "1"), si falta la persona de la que depende,
     *         o si los años de nacimiento no cumplen las reglas
     */
    public Persona agregar(Persona persona) {
        if (persona == null) {
            throw new IllegalArgumentException("La persona no puede ser nula.");
        }
        persona.validar();
        String pos = persona.getPosicion();

        if (contiene(pos) || Posicion.esSujeto(pos)) {
            throw new ArbolInvalidoException("La posición ya existe en el árbol: " + pos);
        }

        if (Posicion.esAscendiente(pos)) {
            String posHijo = Posicion.posicionDelHijoEnLinea(pos);
            Nodo nodoHijo = buscarNodo(posHijo);
            if (nodoHijo == null) {
                throw new ArbolInvalidoException("Falta la persona de la que depende (hijo " + posHijo + ").");
            }
            if (persona.getNacimiento() >= nodoHijo.persona.getNacimiento()) {
                throw new ArbolInvalidoException("El ascendiente debe haber nacido antes que su hijo.");
            }

            int n = Integer.parseInt(pos);
            Nodo nuevo = new Nodo(persona);
            if (n % 2 == 0) {
                nodoHijo.padre = nuevo;
            } else {
                nodoHijo.madre = nuevo;
            }
            tamano++;
            return persona;
        } else if (Posicion.esDescendiente(pos)) {
            String posProg = Posicion.posicionDelProgenitor(pos);
            Nodo nodoProg = buscarNodo(posProg);
            if (nodoProg == null) {
                throw new ArbolInvalidoException("Falta la persona de la que depende (progenitor " + posProg + ").");
            }
            if (persona.getNacimiento() <= nodoProg.persona.getNacimiento()) {
                throw new ArbolInvalidoException("El descendiente debe haber nacido después que su progenitor.");
            }

            Nodo nuevo = new Nodo(persona);
            nodoProg.hijos.add(nuevo);
            tamano++;
            return persona;
        } else {
            throw new ArbolInvalidoException("Posición inválida: " + pos);
        }
    }

    /**
     * Agrega el PADRE de la persona de esa posición, en la posición 2n (ver Posicion.posicionDelPadre).
     * Solo se pueden agregar padres al sujeto y a sus ascendientes.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un ascendiente, si ese padre
     *         ya existe o si no cumple las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("No existe la persona en la posición: " + posicion);
        }
        if (!Posicion.esSujeto(posicion) && !Posicion.esAscendiente(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar padres al sujeto y a sus ascendientes.");
        }
        String posPadre = Posicion.posicionDelPadre(posicion);
        if (!Posicion.esAhnentafel(posPadre)) {
            throw new ArbolInvalidoException("La posición del padre supera el límite permitido.");
        }
        if (nodo.padre != null) {
            throw new ArbolInvalidoException("La persona ya tiene un padre registrado.");
        }
        Persona persona = new Persona(posPadre, nombre, nacimiento, fallecimiento);
        return agregar(persona);
    }

    /**
     * Igual que agregarPadre, pero agrega la MADRE (posición 2n + 1).
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
       Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("No existe la persona en la posición: " + posicion);
        }
        if (!Posicion.esSujeto(posicion) && !Posicion.esAscendiente(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar madres al sujeto y a sus ascendientes.");
        }
        String posMadre = Posicion.posicionDeLaMadre(posicion);
        if (!Posicion.esAhnentafel(posMadre)) {
            throw new ArbolInvalidoException("La posición de la madre supera el límite permitido.");
        }
        if (nodo.madre != null) {
            throw new ArbolInvalidoException("La persona ya tiene una madre registrada.");
        }
        Persona persona = new Persona(posMadre, nombre, nacimiento, fallecimiento);
        return agregar(persona);
    }

    /**
     * Agrega un HIJO a la persona de esa posición. Solo se pueden agregar hijos al sujeto y a sus descendientes.
     * Su posición es la del progenitor + "." + el número siguiente al MAYOR número de hijo que ya exista
     * (o 1 si todavía no tiene hijos). Ej.: si "1" tiene los hijos 1.1 y 1.3, el nuevo es 1.4.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un descendiente, o si no cumple
     *         las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("No existe la persona en la posición: " + posicion);
        }
        if (!Posicion.esSujeto(posicion) && !Posicion.esDescendiente(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar hijos al sujeto y a sus descendientes.");
        }
        int maxOrden = 0;
        for (Nodo hijo : nodo.hijos) {
            int ord = Posicion.orden(hijo.persona.getPosicion());
            if (ord > maxOrden) {
                maxOrden = ord;
            }
        }
        String posHijo = posicion + "." + (maxOrden + 1);
        Persona persona = new Persona(posHijo, nombre, nacimiento, fallecimiento);
        return agregar(persona);
    }

    /**
     * Elimina a la persona de esa posición y TODO lo que depende de ella:
     *   - si es un ascendiente: ella y todos sus propios ascendientes (sus padres, abuelos...);
     *   - si es un descendiente: ella y todos sus propios descendientes (hijos, nietos...).
     * Las demás posiciones NO cambian de número.
     *
     * @return cuántas personas se eliminaron (ella incluida)
     * @throws ArbolInvalidoException si la posición es "1" (el sujeto no se puede eliminar) o si no existe
     */
    public int eliminar(String posicion) {
        if (Posicion.esSujeto(posicion)) {
            throw new ArbolInvalidoException("No se puede eliminar el sujeto (posición 1).");
        }
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("No existe la persona en la posición: " + posicion);
        }

        int eliminados = contarSubarbol(nodo);

        if (Posicion.esAscendiente(posicion)) {
            String posHijo = Posicion.posicionDelHijoEnLinea(posicion);
            Nodo nodoHijo = buscarNodo(posHijo);
            int n = Integer.parseInt(posicion);
            if (n % 2 == 0) {
                nodoHijo.padre = null;
            } else {
                nodoHijo.madre = null;
            }
        } else if (Posicion.esDescendiente(posicion)) {
            String posProg = Posicion.posicionDelProgenitor(posicion);
            Nodo nodoProg = buscarNodo(posProg);
            nodoProg.hijos.remove(nodo);
        }

        tamano -= eliminados;
        return eliminados;
        }

    private int contarSubarbol(Nodo nodo) {
        if (nodo == null) {
            return 0;
        }
        int count = 1;
        count += contarSubarbol(nodo.padre);
        count += contarSubarbol(nodo.madre);
        for (Nodo hijo : nodo.hijos) {
            count += contarSubarbol(hijo);
        }
        return count;
    }

    /**
     * @return todos los miembros en este orden: el sujeto; luego los ascendientes por número Ahnentafel
     *         ascendente (2, 3, 4, 5...); luego los descendientes en PREORDEN (cada persona y después sus hijos:
     *         1.1, 1.1.1, 1.1.2, 1.2, ...). La lista es una copia nueva: modificarla no cambia el árbol.
     *         Este orden garantiza que cada persona aparece DESPUÉS de la que necesita (útil para guardar y cargar).
     */
    public List<Persona> miembros() {
        List<Persona> resultado = new ArrayList<>();
        resultado.add(raiz.persona);
        resultado.addAll(ascendientes());
        resultado.addAll(descendientes());
        return resultado;
    }

    /** @return solo los ascendientes, por número Ahnentafel ascendente (sin el sujeto) */
    public List<Persona> ascendientes() {
       List<Persona> lista = new ArrayList<>();
        recolectarAscendientes(raiz, lista);
        lista.sort(Comparator.comparingInt(p -> Integer.parseInt(p.getPosicion())));
        return lista;
    }

    private void recolectarAscendientes(Nodo actual, List<Persona> lista) {
        if (actual == null) {
            return;
        }
        if (actual.padre != null) {
            lista.add(actual.padre.persona);
            recolectarAscendientes(actual.padre, lista);
        }
        if (actual.madre != null) {
            lista.add(actual.madre.persona);
            recolectarAscendientes(actual.madre, lista);
        }
    }

    /** @return solo los descendientes, en preorden (sin el sujeto) */
    public List<Persona> descendientes() {
        List<Persona> lista = new ArrayList<>();
        recolectarDescendientes(raiz, lista);
        return lista;
    }

    private void recolectarDescendientes(Nodo actual, List<Persona> lista) {
        if (actual == null) {
            return;
        }
        for (Nodo hijo : actual.hijos) {
            lista.add(hijo.persona);
            recolectarDescendientes(hijo, lista);
        }
    }
}
