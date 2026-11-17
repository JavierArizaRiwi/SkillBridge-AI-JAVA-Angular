package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-22T09:30:00Z");

    private final UUID userId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private ProviderRequest pending() {
        return ProviderRequest.create(userId, "Quiero ofrecer mentorías de Java", NOW);
    }

    // ---------- Creación ----------

    @Test
    void una_solicitud_nueva_nace_pendiente_y_conserva_usuario_y_fecha() {
        ProviderRequest r = pending();

        assertNotNull(r.id());
        assertEquals(userId, r.userId());
        assertEquals(ProviderRequestStatus.PENDING, r.status());
        assertTrue(r.isPending());
        assertEquals(NOW, r.createdAt());
        assertNull(r.decidedBy());
        assertNull(r.decidedAt());
        assertNull(r.decisionReason());
    }

    @Test
    void el_motivo_se_recorta_de_espacios() {
        assertEquals("Hola", ProviderRequest.create(userId, "   Hola  ", NOW).message());
    }

    @Test
    void el_motivo_es_obligatorio() {
        assertThrows(IllegalArgumentException.class, () -> ProviderRequest.create(userId, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> ProviderRequest.create(userId, "   ", NOW));
    }

    @Test
    void el_motivo_acepta_500_caracteres_y_rechaza_501() {
        assertDoesNotThrow(() -> ProviderRequest.create(userId, "a".repeat(500), NOW));
        assertThrows(IllegalArgumentException.class, () -> ProviderRequest.create(userId, "a".repeat(501), NOW));
    }

    // ---------- Transiciones válidas ----------

    @Test
    void pendiente_se_puede_aprobar_y_queda_registrada_la_decision() {
        ProviderRequest original = pending();

        ProviderRequest approved = original.approve(adminId, "Perfil verificado", LATER);

        assertEquals(ProviderRequestStatus.APPROVED, approved.status());
        assertEquals(adminId, approved.decidedBy());
        assertEquals(LATER, approved.decidedAt());
        assertEquals("Perfil verificado", approved.decisionReason());
        assertEquals(original.id(), approved.id());
        assertEquals(original.message(), approved.message());
        assertEquals(original.createdAt(), approved.createdAt());
        assertTrue(original.isPending()); // el original no se modifica
    }

    @Test
    void pendiente_se_puede_rechazar() {
        ProviderRequest rejected = pending().reject(adminId, "Falta información", LATER);

        assertEquals(ProviderRequestStatus.REJECTED, rejected.status());
        assertEquals(adminId, rejected.decidedBy());
        assertFalse(rejected.isPending());
    }

    @Test
    void la_nota_del_admin_es_opcional() {
        assertNull(pending().approve(adminId, null, LATER).decisionReason());
        assertNull(pending().reject(adminId, "   ", LATER).decisionReason());
    }

    @Test
    void la_nota_del_admin_no_puede_superar_500_caracteres() {
        assertThrows(IllegalArgumentException.class, () -> pending().approve(adminId, "a".repeat(501), LATER));
    }

    // ---------- Transiciones inválidas ----------

    @Test
    void una_solicitud_aprobada_no_se_puede_aprobar_ni_rechazar() {
        ProviderRequest approved = pending().approve(adminId, null, LATER);

        assertThrows(BusinessRuleException.class, () -> approved.approve(adminId, null, LATER));
        assertThrows(BusinessRuleException.class, () -> approved.reject(adminId, null, LATER));
    }

    @Test
    void una_solicitud_rechazada_no_se_puede_aprobar_ni_rechazar() {
        ProviderRequest rejected = pending().reject(adminId, null, LATER);

        assertThrows(BusinessRuleException.class, () -> rejected.approve(adminId, null, LATER));
        assertThrows(BusinessRuleException.class, () -> rejected.reject(adminId, null, LATER));
    }
}
