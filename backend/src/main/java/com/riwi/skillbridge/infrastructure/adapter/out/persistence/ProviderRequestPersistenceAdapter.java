package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.ProviderRequestPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ProviderRequestEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaProviderRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProviderRequestPersistenceAdapter implements ProviderRequestPort {

    private static final String PENDING_INDEX = "uq_provider_requests_pending";

    private final JpaProviderRequestRepository repository;

    public ProviderRequestPersistenceAdapter(JpaProviderRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProviderRequest save(ProviderRequest request) {
        try {
            // saveAndFlush: la violación del índice único debe saltar aquí, no al cerrar la transacción.
            return toDomain(repository.saveAndFlush(toEntity(request)));
        } catch (DataIntegrityViolationException e) {
            if (violatesPendingIndex(e)) {
                throw new BusinessRuleException("Ya existe una solicitud pendiente para este usuario");
            }
            throw e;
        }
    }

    @Override
    public Optional<ProviderRequest> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ProviderRequest> findByUserId(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<ProviderRequest> findByStatus(ProviderRequestStatus status, PageQuery query) {
        Sort.Direction direction = query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        // El desempate por id mantiene estable el orden entre páginas.
        Sort sort = Sort.by(direction, query.sortBy()).and(Sort.by(Sort.Direction.ASC, "id"));
        Page<ProviderRequestEntity> page =
            repository.findByStatus(status, PageRequest.of(query.page(), query.size(), sort));
        return new PageResult<>(
            page.getContent().stream().map(this::toDomain).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static boolean violatesPendingIndex(Throwable error) {
        for (Throwable c = error; c != null; c = (c.getCause() == c ? null : c.getCause())) {
            if (c.getMessage() != null && c.getMessage().contains(PENDING_INDEX)) {
                return true;
            }
        }
        return false;
    }

    private ProviderRequestEntity toEntity(ProviderRequest r) {
        return new ProviderRequestEntity(r.id(), r.userId(), r.status(), r.message(),
            r.createdAt(), r.decidedBy(), r.decidedAt(), r.decisionReason());
    }

    private ProviderRequest toDomain(ProviderRequestEntity e) {
        return new ProviderRequest(e.getId(), e.getUserId(), e.getStatus(), e.getMessage(),
            e.getCreatedAt(), e.getDecidedBy(), e.getDecidedAt(), e.getDecisionReason());
    }
}
