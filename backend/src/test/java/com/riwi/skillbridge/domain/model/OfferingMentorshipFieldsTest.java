package com.riwi.skillbridge.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfferingMentorshipFieldsTest {

    private static final UUID PROVIDER = UUID.randomUUID();

    @Test
    void create_sin_modalidad_ni_duracion_usa_valores_por_defecto() {
        Offering o = Offering.create(PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN);
        assertEquals(MentorshipMode.ASYNCHRONOUS, o.mode());
        assertEquals(1, o.durationHours());
    }

    @Test
    void create_respeta_modalidad_y_duracion() {
        Offering o = Offering.create(PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN, MentorshipMode.LIVE, 3);
        assertEquals(MentorshipMode.LIVE, o.mode());
        assertEquals(3, o.durationHours());
    }

    @Test
    void constructor_de_siete_argumentos_y_cache_antigua_normalizan_a_valores_por_defecto() {
        Offering o = new Offering(UUID.randomUUID(), PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN, true);
        assertEquals(MentorshipMode.ASYNCHRONOUS, o.mode());
        assertEquals(1, o.durationHours());
    }

    @Test
    void update_con_valores_nulos_conserva_los_actuales() {
        Offering o = Offering.create(PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN, MentorshipMode.LIVE, 2);
        Offering updated = o.update("Java 21", "d", "BACKEND", BigDecimal.ONE);
        assertEquals(MentorshipMode.LIVE, updated.mode());
        assertEquals(2, updated.durationHours());
        assertEquals("Java 21", updated.title());
    }

    @Test
    void update_puede_cambiar_modalidad_y_duracion() {
        Offering o = Offering.create(PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN);
        Offering updated = o.update("Java", "d", "BACKEND", BigDecimal.TEN, MentorshipMode.LIVE, 4);
        assertEquals(MentorshipMode.LIVE, updated.mode());
        assertEquals(4, updated.durationHours());
    }

    @Test
    void activar_y_desactivar_conservan_modalidad_y_duracion() {
        Offering o = Offering.create(PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN, MentorshipMode.LIVE, 5);
        assertEquals(5, o.deactivate().durationHours());
        assertEquals(MentorshipMode.LIVE, o.deactivate().activate().mode());
    }

    @Test
    void duracion_fuera_de_rango_es_rechazada() {
        assertThrows(IllegalArgumentException.class,
            () -> Offering.create(PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN, MentorshipMode.LIVE, 25));
        assertThrows(IllegalArgumentException.class,
            () -> Offering.create(PROVIDER, "Java", "d", "BACKEND", BigDecimal.TEN, MentorshipMode.LIVE, -1));
    }
}
