package com.riwi.skillbridge.domain.model;

public record PageQuery(int page, int size, String sortBy, boolean descending) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("El tamaño debe estar entre 1 y " + MAX_SIZE);
        }
        if (sortBy == null || sortBy.isBlank()) {
            throw new IllegalArgumentException("El campo de orden es obligatorio");
        }
    }
}
