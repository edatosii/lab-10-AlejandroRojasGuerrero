package co.umariana.construccion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;

/**
 * Construye un árbol genealógico a partir de un texto: cada línea es una persona.
 *
 *     posicion;nombre;nacimiento;fallecimiento
 *
 * Ejemplo (el fallecimiento puede quedar vacío):
 *
 *     1;John Doe;2002;
 *     2;Henry Doe;1920;1990
 *     3;Maria Doe;1921;
 *     1.1;Timmy Doe;2021;
 *     1.2;Amy Doe;2026;
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 3)
 * ------------------------------------------
 * Reemplace el "throw new UnsupportedOperationException" por su implementación. Esta clase usa lo que ya
 * hizo en las etapas anteriores (Posicion, Persona y ArbolGenealogico). NO modifique las pruebas (src/test).
 *
 * REGLAS
 *   1. Si el texto es null o no tiene ninguna línea con contenido: ArbolInvalidoException (mensaje: "El texto está vacío...").
 *   2. Las líneas pueden terminar en \n o en \r\n. Las líneas en blanco se ignoran, pero los números de línea
 *      que se informan cuentan TODAS las líneas del texto (la primera es la línea 1).
 *   3. Cada línea tiene 4 campos separados por ';' (el cuarto, el fallecimiento, puede estar vacío). Se acepta
 *      también la línea con solo 3 campos y sin el último ';'. Los campos se recortan (trim).
 *      Si hay menos de 3 o más de 4 campos: error.
 *   4. El nacimiento y el fallecimiento deben ser números enteros (el fallecimiento vacío es "no tiene").
 *   5. Cada persona debe ser válida (Persona.validar) y no puede haber dos líneas con la misma posición.
 *   6. Debe existir la persona "1" (el sujeto), pero puede estar en cualquier línea.
 *   7. Las líneas pueden venir en CUALQUIER orden. El constructor debe agregarlas al árbol en un orden en el
 *      que cada persona ya tenga a la que necesita (por ejemplo, primero los ascendientes por número Ahnentafel
 *      ascendente y los descendientes por generación). Las reglas del árbol (ver ArbolGenealogico) las valida el árbol.
 *   8. Todos los errores asociados a una línea se informan con ArbolInvalidoException y un mensaje que EMPIEZA con
 *      "Línea N: " (N es el número de línea de la persona con el problema), por ejemplo:
 *          "Línea 3: la posición 2 está repetida."
 *      Los errores que no son de una línea (texto vacío, falta el sujeto) no llevan ese prefijo.
 *   9. Solo se muestran los miembros que están en el texto: no se crean personas vacías para los que faltan.
 */
public final class ConstructorArbol {

    private ConstructorArbol() {
    }
    private static class ElementoLinea {
        final int numeroLinea;
        final Persona persona;

        ElementoLinea(int numeroLinea, Persona persona) {
            this.numeroLinea = numeroLinea;
            this.persona = persona;
        }
    }
    public static ArbolGenealogico construir(String texto) {
if (texto == null) {
            throw new ArbolInvalidoException("El texto está vacío.");
        }

        String[] lineas = texto.split("\r?\n", -1);
        List<ElementoLinea> elementos = new ArrayList<>();
        Map<String, Integer> posicionesVistas = new HashMap<>();

        for (int i = 0; i < lineas.length; i++) {
            int numLinea = i + 1;
            String linea = lineas[i];
            if (linea.strip().isEmpty()) {
                continue;
            }

            String[] campos = linea.split(";", -1);
            if (campos.length < 3 || campos.length > 4) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": formato de campos inválido.");
            }

            String pos = campos[0].trim();
            String nom = campos[1].trim();
            String nacStr = campos[2].trim();
            String falStr = (campos.length == 4) ? campos[3].trim() : "";

            int nacimiento;
            try {
                nacimiento = Integer.parseInt(nacStr);
            } catch (NumberFormatException e) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": año de nacimiento inválido.");
            }

            Integer fallecimiento = null;
            if (!falStr.isEmpty()) {
                try {
                    fallecimiento = Integer.parseInt(falStr);
                } catch (NumberFormatException e) {
                    throw new ArbolInvalidoException("Línea " + numLinea + ": año de fallecimiento inválido.");
                }
            }

            Persona persona;
            try {
                persona = new Persona(pos, nom, nacimiento, fallecimiento);
                persona.validar();
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": " + e.getMessage());
            }

            if (posicionesVistas.containsKey(pos)) {
                throw new ArbolInvalidoException("Línea " + numLinea + ": la posición " + pos + " está repetida.");
            }
            posicionesVistas.put(pos, numLinea);

            elementos.add(new ElementoLinea(numLinea, persona));
        }

        if (elementos.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío.");
        }

        ElementoLinea elemSujeto = null;
        for (ElementoLinea elem : elementos) {
            if (Posicion.esSujeto(elem.persona.getPosicion())) {
                elemSujeto = elem;
                break;
            }
        }

        if (elemSujeto == null) {
            throw new ArbolInvalidoException("Falta la persona en la posición 1 (el sujeto).");
        }

        ArbolGenealogico arbol;
        try {
            arbol = new ArbolGenealogico(elemSujeto.persona);
        } catch (RuntimeException e) {
            throw new ArbolInvalidoException("Línea " + elemSujeto.numeroLinea + ": " + e.getMessage());
        }

        List<ElementoLinea> ascendientes = new ArrayList<>();
        List<ElementoLinea> descendientes = new ArrayList<>();

        for (ElementoLinea elem : elementos) {
            if (Posicion.esSujeto(elem.persona.getPosicion())) {
                continue;
            }
            if (Posicion.esAscendiente(elem.persona.getPosicion())) {
                ascendientes.add(elem);
            } else if (Posicion.esDescendiente(elem.persona.getPosicion())) {
                descendientes.add(elem);
            }
        }

        ascendientes.sort(Comparator.comparingInt(e -> Integer.parseInt(e.persona.getPosicion())));
        descendientes.sort(Comparator.comparingInt(e -> Posicion.generacion(e.persona.getPosicion())));

        for (ElementoLinea elem : ascendientes) {
            try {
                arbol.agregar(elem.persona);
            } catch (RuntimeException e) {
                throw new ArbolInvalidoException("Línea " + elem.numeroLinea + ": " + e.getMessage());
            }
        }

        for (ElementoLinea elem : descendientes) {
            try {
                arbol.agregar(elem.persona);
            } catch (RuntimeException e) {
                throw new ArbolInvalidoException("Línea " + elem.numeroLinea + ": " + e.getMessage());
            }
        }

        return arbol;
    }
}
