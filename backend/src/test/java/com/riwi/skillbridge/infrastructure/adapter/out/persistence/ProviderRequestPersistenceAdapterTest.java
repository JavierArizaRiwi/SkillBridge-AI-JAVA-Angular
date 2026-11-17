package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProviderRequestPersistenceAdapter.class)
class ProviderRequestPersistenceAdapterTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
        .withDatabaseName("skillbridge_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // Usuarios que crea el seed de Flyway (las claves foráneas exigen que existan)
    private static final UUID CUSTOMER_ID = UUID.fromString("b7d9f3c1-a8e4-4c59-b1d6-8f2a3e9c4b7d");
    private static final UUID ADMIN_ID = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");
    private static final Instant T0 = Instant.parse("2026-10-21T12:00:00Z");

    @Autowired
    ProviderRequestPersistenceAdapter adapter;

    private ProviderRequest pending(UUID userId, Instant at) {
        return ProviderRequest.create(userId, "Quiero ofrecer mentorías", at);
    }

    @Test
    void guarda_y_recupera_la_solicitud_con_todos_sus_campos() {
        ProviderRequest saved = adapter.save(pending(CUSTOMER_ID, T0));

        ProviderRequest found = adapter.findById(saved.id()).orElseThrow();

        assertThat(found).isEqualTo(saved);
        assertThat(found.status()).isEqualTo(ProviderRequestStatus.PENDING);
        assertThat(found.userId()).isEqualTo(CUSTOMER_ID);
        assertThat(found.createdAt()).isEqualTo(T0);
    }

    @Test
    void una_segunda_solicitud_pendiente_del_mismo_usuario_es_rechazada_por_el_indice_unico() {
        adapter.save(pending(CUSTOMER_ID, T0));

        assertThatThrownBy(() -> adapter.save(pending(CUSTOMER_ID, T0.plusSeconds(60))))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void despues_de_una_decision_el_usuario_puede_volver_a_solicitar() {
        ProviderRequest first = adapter.save(pending(CUSTOMER_ID, T0));
        adapter.save(first.reject(ADMIN_ID, "Falta información", T0.plusSeconds(3600)));

        ProviderRequest second = adapter.save(pending(CUSTOMER_ID, T0.plusSeconds(7200)));

        assertThat(second.isPending()).isTrue();
        assertThat(adapter.findById(first.id()).orElseThrow().status()).isEqualTo(ProviderRequestStatus.REJECTED);
    }

    @Test
    void la_decision_del_admin_queda_registrada() {
        ProviderRequest saved = adapter.save(pending(CUSTOMER_ID, T0));

        adapter.save(saved.approve(ADMIN_ID, "Perfil verificado", T0.plusSeconds(600)));
        ProviderRequest found = adapter.findById(saved.id()).orElseThrow();

        assertThat(found.status()).isEqualTo(ProviderRequestStatus.APPROVED);
        assertThat(found.decidedBy()).isEqualTo(ADMIN_ID);
        assertThat(found.decidedAt()).isEqualTo(T0.plusSeconds(600));
        assertThat(found.decisionReason()).isEqualTo("Perfil verificado");
    }

    @Test
    void las_solicitudes_de_un_usuario_salen_de_la_mas_reciente_a_la_mas_antigua() {
        ProviderRequest older = adapter.save(pending(CUSTOMER_ID, T0));
        adapter.save(older.reject(ADMIN_ID, null, T0.plusSeconds(60)));
        ProviderRequest newer = adapter.save(pending(CUSTOMER_ID, T0.plusSeconds(120)));

        List<ProviderRequest> mine = adapter.findByUserId(CUSTOMER_ID);

        assertThat(mine).extracting(ProviderRequest::id).containsExactly(newer.id(), older.id());
    }

    @Test
    void un_usuario_sin_solicitudes_devuelve_una_lista_vacia() {
        assertThat(adapter.findByUserId(ADMIN_ID)).isEmpty();
    }

    @Test
    void lista_solo_las_pendientes_paginadas_y_ordenadas_de_la_mas_antigua() {
        ProviderRequest pendiente = adapter.save(pending(CUSTOMER_ID, T0));
        ProviderRequest resuelta = adapter.save(pending(ADMIN_ID, T0.plusSeconds(10)));
        adapter.save(resuelta.reject(ADMIN_ID, null, T0.plusSeconds(20)));

        PageResult<ProviderRequest> page =
            adapter.findByStatus(ProviderRequestStatus.PENDING, new PageQuery(0, 10, "createdAt", false));

        assertThat(page.items()).extracting(ProviderRequest::id).containsExactly(pendiente.id());
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }
}
