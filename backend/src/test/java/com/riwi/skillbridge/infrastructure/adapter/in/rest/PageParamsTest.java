package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.domain.model.PageQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageParamsTest {

    @Test
    void convierte_campo_y_direccion_descendente() {
        PageQuery q = PageParams.toQuery(2, 10, "price,desc");
        assertEquals(2, q.page());
        assertEquals(10, q.size());
        assertEquals("price", q.sortBy());
        assertTrue(q.descending());
    }

    @Test
    void sin_direccion_es_ascendente() {
        assertFalse(PageParams.toQuery(0, 20, "title").descending());
    }

    @Test
    void direccion_invalida_se_rechaza() {
        assertThrows(IllegalArgumentException.class, () -> PageParams.toQuery(0, 20, "title,sideways"));
    }

    @Test
    void pagina_negativa_y_tamano_fuera_de_rango_se_rechazan() {
        assertThrows(IllegalArgumentException.class, () -> PageParams.toQuery(-1, 20, "title"));
        assertThrows(IllegalArgumentException.class, () -> PageParams.toQuery(0, 0, "title"));
        assertThrows(IllegalArgumentException.class, () -> PageParams.toQuery(0, PageQuery.MAX_SIZE + 1, "title"));
    }
}
