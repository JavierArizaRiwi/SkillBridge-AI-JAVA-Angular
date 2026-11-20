package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ActivateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.CreateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.DeactivateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.UpdateOfferingUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.policy.OfferingAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OfferingCommandService implements CreateOfferingUseCase, UpdateOfferingUseCase,
    DeactivateOfferingUseCase, ActivateOfferingUseCase {

    private final OfferingRepositoryPort repository;
    private final OfferingCachePort cache;
    private final OfferingAccessPolicy policy;
    private final AuditEventPublisherPort auditPublisher;

    public OfferingCommandService(OfferingRepositoryPort repository,
                                  OfferingCachePort cache,
                                  OfferingAccessPolicy policy,
                                  AuditEventPublisherPort auditPublisher) {
        this.repository = repository;
        this.cache = cache;
        this.policy = policy;
        this.auditPublisher = auditPublisher;
    }

    @Override
    public Offering create(Actor actor, CreateOfferingUseCase.Command command) {
        UUID ownerId = resolveOwner(actor, command.providerId());
        Offering saved = repository.save(Offering.create(ownerId, command.title(),
            command.description(), command.category(), command.price()));
        cache.evictActiveOfferings();
        
        publishAudit("OfferingCreated", actor, "CREATE", "OFFERING", saved.id().toString());
        return saved;
    }

    @Override
    public Offering update(Actor actor, UUID offeringId, UpdateOfferingUseCase.Command command) {
        Offering offering = load(offeringId);
        policy.assertCanModify(actor, offering);
        Offering saved = repository.save(offering.update(command.title(),
            command.description(), command.category(), command.price()));
        cache.evictActiveOfferings();
        
        publishAudit("OfferingUpdated", actor, "UPDATE", "OFFERING", saved.id().toString());
        return saved;
    }

    @Override
    public void deactivate(Actor actor, UUID offeringId) {
        Offering offering = load(offeringId);
        policy.assertCanModify(actor, offering);
        if (!offering.active()) return;          // idempotente
        repository.save(offering.deactivate());
        cache.evictActiveOfferings();
        
        publishAudit("OfferingDeactivated", actor, "DEACTIVATE", "OFFERING", offering.id().toString());
    }

    @Override
    public void activate(Actor actor, UUID offeringId) {
        Offering offering = load(offeringId);
        policy.assertCanModify(actor, offering);
        if (offering.active()) return;           // idempotente
        repository.save(offering.activate());
        cache.evictActiveOfferings();
        
        publishAudit("OfferingActivated", actor, "ACTIVATE", "OFFERING", offering.id().toString());
    }

    private void publishAudit(String eventType, Actor actor, String action, String resource, String resourceId) {
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BusinessEvent<String> event = new BusinessEvent<>(
            UUID.randomUUID(), eventType, resourceId, resource, Instant.now(), correlationId, 1, eventType,
            actor.id().toString(), actor.username(), actor.role().name(), action, resource, resourceId
        );
        auditPublisher.publish(event);
    }

    private Offering load(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Offering no encontrado"));
    }

    private UUID resolveOwner(Actor actor, UUID requestedProviderId) {
        return switch (actor.role()) {
            case PROVIDER -> actor.id();   // se ignora lo que venga en la petición
            case ADMIN -> {
                if (requestedProviderId == null) {
                    throw new IllegalArgumentException("providerId es obligatorio para Admin");
                }
                yield requestedProviderId;
            }
            case CUSTOMER -> throw new ForbiddenOperationException("No tienes permiso para crear offerings");
        };
    }
}
