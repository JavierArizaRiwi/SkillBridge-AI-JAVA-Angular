package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService implements AuthUseCase {
    private final UserRepositoryPort users;
    private final PasswordHasherPort passwords;
    private final TokenPort tokens;
    private final AuditEventPublisherPort auditPublisher;

    public AuthService(UserRepositoryPort users, PasswordHasherPort passwords, TokenPort tokens, AuditEventPublisherPort auditPublisher) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.auditPublisher = auditPublisher;
    }

    @Override
    public String register(String name, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException("El correo ya está registrado");
        }
        UserAccount saved = users.save(new UserAccount(
            UUID.randomUUID(), name.trim(), normalizedEmail, passwords.encode(rawPassword), Role.CUSTOMER));

        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BusinessEvent<String> event = new BusinessEvent<>(
            UUID.randomUUID(), "UserRegistered", saved.id().toString(), "User", Instant.now(), correlationId, 1, "User registered",
            saved.id().toString(), saved.email(), saved.role().name(), "REGISTER", "USER", saved.id().toString()
        );
        auditPublisher.publish(event);

        return tokens.generate(saved.email(), saved.role().name());
    }

    @Override
    public String login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas"));
        if (!passwords.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException("Credenciales inválidas");
        }
        // Se revisa después de validar la contraseña, para no revelar el estado de una cuenta ajena,
        // y antes de auditar el login y emitir el token.
        if (user.status() == UserStatus.SUSPENDED) {
            throw new ForbiddenOperationException("La cuenta está suspendida");
        }

        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BusinessEvent<String> event = new BusinessEvent<>(
            UUID.randomUUID(), "UserLoggedIn", user.id().toString(), "User", Instant.now(), correlationId, 1, "User logged in",
            user.id().toString(), user.email(), user.role().name(), "LOGIN", "USER", user.id().toString()
        );
        auditPublisher.publish(event);

        return tokens.generate(user.email(), user.role().name());
    }
}
