package com.riwi.skillbridge.infrastructure.security;

import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {

    private final JpaUserRepository repository = mock(JpaUserRepository.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(repository);

    private void conUsuario(UserStatus status) {
        when(repository.findByEmailIgnoreCase("ana@test.com")).thenReturn(Optional.of(
            new UserEntity(UUID.randomUUID(), "Ana", "ana@test.com", "hash", Role.CUSTOMER, status, Instant.now())));
    }

    @Test
    void cuenta_activa_esta_habilitada() {
        conUsuario(UserStatus.ACTIVE);

        assertTrue(service.loadUserByUsername("ana@test.com").isEnabled());
    }

    @Test
    void cuenta_suspendida_esta_deshabilitada() {
        conUsuario(UserStatus.SUSPENDED);

        assertFalse(service.loadUserByUsername("ana@test.com").isEnabled());
    }
}
