package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.UpdateOfferingUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.policy.OfferingAccessPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que el catálogo cacheado nunca queda desactualizado después de una escritura.
 * Usa repositorio y caché en memoria para probar el comportamiento real, no solo las llamadas.
 */
class OfferingCacheConsistencyTest {

    private final InMemoryRepository repository = new InMemoryRepository();
    private final InMemoryCache cache = new InMemoryCache();
    private final DummyAuditPublisher auditPublisher = new DummyAuditPublisher();
    private final OfferingCommandService commands =
        new OfferingCommandService(repository, cache, new OfferingAccessPolicy(), auditPublisher);
    private final OfferingService catalog = new OfferingService(repository, cache);

    private final Actor provider = new Actor(UUID.randomUUID(), Role.PROVIDER);

    private Offering createOne(String title) {
        return commands.create(provider,
            new CreateOfferingUseCase.Command(null, title, "desc", "BACKEND", BigDecimal.TEN));
    }

    @Test
    void crear_refresca_el_catalogo_cacheado() {
        assertTrue(catalog.listActive().isEmpty());   // calienta la caché con lista vacía

        createOne("Java");

        assertEquals(1, catalog.listActive().size());
    }

    @Test
    void actualizar_refresca_el_catalogo_cacheado() {
        Offering created = createOne("Java");
        catalog.listActive();                         // calienta la caché con el título viejo

        commands.update(provider, created.id(),
            new UpdateOfferingUseCase.Command("Java 21", "desc", "BACKEND", BigDecimal.TEN));

        assertEquals("Java 21", catalog.listActive().get(0).title());
    }

    @Test
    void desactivar_y_activar_refrescan_el_catalogo_cacheado() {
        Offering created = createOne("Java");
        assertEquals(1, catalog.listActive().size());

        commands.deactivate(provider, created.id());
        assertTrue(catalog.listActive().isEmpty());

        commands.activate(provider, created.id());
        assertEquals(1, catalog.listActive().size());
    }

    private static class InMemoryRepository implements OfferingRepositoryPort {
        private final Map<UUID, Offering> store = new LinkedHashMap<>();

        @Override public List<Offering> findAllActive() {
            return store.values().stream().filter(Offering::active).toList();
        }
        @Override public List<Offering> findAll() { return List.copyOf(store.values()); }
        @Override public List<Offering> findByProviderId(UUID providerId) {
            return store.values().stream().filter(o -> o.providerId().equals(providerId)).toList();
        }
        @Override public Optional<Offering> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Offering save(Offering offering) { store.put(offering.id(), offering); return offering; }
    }

    private static class InMemoryCache implements OfferingCachePort {
        private List<Offering> cached;

        @Override public Optional<List<Offering>> getActiveOfferings() { return Optional.ofNullable(cached); }
        @Override public void putActiveOfferings(List<Offering> offerings) { cached = offerings; }
        @Override public void evictActiveOfferings() { cached = null; }
    }

    private static class DummyAuditPublisher implements AuditEventPublisherPort {
        @Override
        public void publish(BusinessEvent<?> event) {
            // do nothing
        }
    }
}
