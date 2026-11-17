package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
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
    public PageResult<Offering> findAllActive(PageQuery query) {
        return toResult(repository.findByActiveTrue(toPageable(query)));
    }

    @Override
    public PageResult<Offering> findAll(PageQuery query) {
        return toResult(repository.findAll(toPageable(query)));
    }

    @Override
    public PageResult<Offering> findByProviderId(UUID providerId, PageQuery query) {
        return toResult(repository.findByProviderId(providerId, toPageable(query)));
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
                    offering.category(), offering.price(), offering.active(),
                    offering.mode(), offering.durationHours());
                return repository.save(existing);
            })
            .orElseGet(() -> repository.save(toEntity(offering)));
        return toDomain(saved);
    }

    private static final Set<String> SORTABLE = Set.of("title", "price", "category", "active", "createdAt");

    private static PageRequest toPageable(PageQuery query) {
        if (!SORTABLE.contains(query.sortBy())) {
            throw new IllegalArgumentException("Campo de orden no permitido. Use: " + String.join(", ", SORTABLE));
        }
        Sort.Direction direction = query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        // El desempate por id mantiene estable el orden entre páginas.
        Sort sort = Sort.by(direction, query.sortBy()).and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(query.page(), query.size(), sort);
    }

    private PageResult<Offering> toResult(Page<OfferingEntity> page) {
        return new PageResult<>(page.getContent().stream().map(this::toDomain).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private Offering toDomain(OfferingEntity e) {
        return new Offering(e.getId(), e.getProviderId(), e.getTitle(), e.getDescription(),
            e.getCategory(), e.getPrice(), e.isActive(), e.getMode(), e.getDurationHours());
    }

    private OfferingEntity toEntity(Offering o) {
        return new OfferingEntity(o.id(), o.providerId(), o.title(), o.description(),
            o.category(), o.price(), o.active(), o.mode(), o.durationHours());
    }
}
