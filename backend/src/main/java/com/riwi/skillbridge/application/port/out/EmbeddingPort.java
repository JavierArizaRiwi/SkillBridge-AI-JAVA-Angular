package com.riwi.skillbridge.application.port.out;

import java.util.List;
import java.util.Objects;

/**
 * Puerto de salida para la generación de vectores de embedding semánticos.
 */
public interface EmbeddingPort {

    /**
     * Genera los vectores de embedding para una lista de textos.
     *
     * @param texts lista de textos a transformar en vectores
     * @return lista de vectores numéricos (double[]) en el mismo orden que los textos de entrada
     */
    List<double[]> embed(List<String> texts);

    /**
     * Sobrecarga conveniente para generar el embedding de un único texto.
     *
     * @param text texto a transformar en vector
     * @return vector numérico (double[])
     */
    default double[] embed(String text) {
        Objects.requireNonNull(text, "text no puede ser nulo");
        List<double[]> embeddings = embed(List.of(text));
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalStateException("El proveedor de embeddings retornó una lista vacía");
        }
        return embeddings.get(0);
    }
}
