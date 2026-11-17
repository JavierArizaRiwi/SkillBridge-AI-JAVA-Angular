package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.domain.model.PageQuery;

/** Convierte los parámetros HTTP page/size/sort ("campo,asc|desc") en un {@link PageQuery}. */
final class PageParams {
    private PageParams() {}

    static PageQuery toQuery(int page, int size, String sort) {
        String[] parts = sort.split(",");
        String direction = parts.length > 1 ? parts[1].trim() : "asc";
        if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("La dirección de orden debe ser asc o desc");
        }
        return new PageQuery(page, size, parts[0].trim(), direction.equalsIgnoreCase("desc"));
    }
}
