package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OfferingPersistenceAdapter implements OfferingRepositoryPort {
    private final JpaOfferingRepository repository;

    public OfferingPersistenceAdapter(JpaOfferingRepository repository) { this.repository = repository; }

    @Override
    public List<Offering> findAllActive() {
        return repository.findByActiveTrueOrderByTitleAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Offering> findAll() {
        return repository.findAllByOrderByTitleAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Offering> findByProviderId(UUID providerId) {
        return repository.findByProviderIdOrderByTitleAsc(providerId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Offering> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Offering save(Offering offering) {
        OfferingEntity saved = repository.findById(offering.id())
            .map(existing -> {
                existing.applyChanges(offering.title(), offering.description(),
                    offering.category(), offering.price(), offering.active());
                return repository.save(existing);
            })
            .orElseGet(() -> repository.save(toEntity(offering)));
        return toDomain(saved);
    }

    private Offering toDomain(OfferingEntity e) {
        return new Offering(e.getId(), e.getProviderId(), e.getTitle(), e.getDescription(),
            e.getCategory(), e.getPrice(), e.isActive());
    }

    private OfferingEntity toEntity(Offering o) {
        return new OfferingEntity(o.id(), o.providerId(), o.title(), o.description(),
            o.category(), o.price(), o.active());
    }
}
