package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort, UserAccountPort, UserAdminPort {
    private final JpaUserRepository repository;

    public UserPersistenceAdapter(JpaUserRepository repository) { this.repository = repository; }

    @Override
    public boolean existsByEmail(String email) { return repository.existsByEmailIgnoreCase(email); }

    @Override
    public Optional<UserAccount> findByEmail(String email) { return repository.findByEmailIgnoreCase(email).map(this::toDomain); }

    @Override
    public Optional<UserAccount> findById(UUID id) { return repository.findById(id).map(this::toDomain); }

    @Override
    public PageResult<UserAccount> findAll(PageQuery query) {
        Sort.Direction direction = query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        // El desempate por id mantiene estable el orden entre páginas.
        Sort sort = Sort.by(direction, query.sortBy()).and(Sort.by(Sort.Direction.ASC, "id"));
        Page<UserEntity> page = repository.findAll(PageRequest.of(query.page(), query.size(), sort));
        return new PageResult<>(
            page.getContent().stream().map(this::toDomain).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public UserAccount save(UserAccount user) {
        // Conserva la fecha de creación original: antes se reiniciaba en cada guardado.
        Instant createdAt = repository.findById(user.id())
            .map(UserEntity::getCreatedAt)
            .orElseGet(Instant::now);
        UserEntity saved = repository.save(new UserEntity(
            user.id(), user.name(), user.email(), user.passwordHash(),
            user.role(), user.status(), createdAt));
        return toDomain(saved);
    }

    @Override
    public Optional<UUID> findIdByEmail(String email) { return findByEmail(email).map(UserAccount::id); }

    private UserAccount toDomain(UserEntity e) {
        return new UserAccount(e.getId(), e.getName(), e.getEmail(), e.getPassword(), e.getRole(), e.getStatus());
    }
}
