package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepositoryPort users;
    private PasswordHasherPort passwords;
    private TokenPort tokens;
    private AuditEventPublisherPort auditPublisher;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserRepositoryPort.class);
        passwords = mock(PasswordHasherPort.class);
        tokens = mock(TokenPort.class);
        auditPublisher = mock(AuditEventPublisherPort.class);
        authService = new AuthService(users, passwords, tokens, auditPublisher);
    }

    private UserAccount account(UserStatus status) {
        return new UserAccount(UUID.randomUUID(), "Ana", "ana@test.com", "hash", Role.CUSTOMER, status);
    }

    @Test
    void testRegisterEmitsEvent() {
        UserAccount savedUser = new UserAccount(UUID.randomUUID(), "Test User", "test@example.com", "hash", Role.CUSTOMER);
        when(users.existsByEmail("test@example.com")).thenReturn(false);
        when(users.save(any(UserAccount.class))).thenReturn(savedUser);
        when(passwords.encode("password")).thenReturn("hash");
        when(tokens.generate("test@example.com", "CUSTOMER")).thenReturn("token");

        authService.register("Test User", "test@example.com", "password");

        ArgumentCaptor<BusinessEvent> captor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(auditPublisher).publish(captor.capture());

        BusinessEvent event = captor.getValue();
        assertEquals("UserRegistered", event.eventType());
        assertEquals("REGISTER", event.action());
        assertEquals("USER", event.resource());
        assertEquals(savedUser.id().toString(), event.actorUserId());
        assertEquals("test@example.com", event.actorUsername());
        assertEquals("CUSTOMER", event.actorRole());
    }

    @Test
    void testLoginEmitsEvent() {
        UserAccount user = new UserAccount(UUID.randomUUID(), "Test User", "test@example.com", "hash", Role.CUSTOMER);
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("password", "hash")).thenReturn(true);
        when(tokens.generate("test@example.com", "CUSTOMER")).thenReturn("token");

        authService.login("test@example.com", "password");

        ArgumentCaptor<BusinessEvent> captor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(auditPublisher).publish(captor.capture());

        BusinessEvent event = captor.getValue();
        assertEquals("UserLoggedIn", event.eventType());
        assertEquals("LOGIN", event.action());
        assertEquals("USER", event.resource());
        assertEquals(user.id().toString(), event.actorUserId());
        assertEquals("test@example.com", event.actorUsername());
        assertEquals("CUSTOMER", event.actorRole());
    }

    @Test
    void login_de_cuenta_activa_devuelve_token() {
        when(users.findByEmail("ana@test.com")).thenReturn(Optional.of(account(UserStatus.ACTIVE)));
        when(passwords.matches("clave", "hash")).thenReturn(true);
        when(tokens.generate("ana@test.com", "CUSTOMER")).thenReturn("jwt");

        assertEquals("jwt", authService.login("ana@test.com", "clave"));
    }

    @Test
    void login_de_cuenta_suspendida_es_rechazado_y_no_emite_token_ni_evento() {
        when(users.findByEmail("ana@test.com")).thenReturn(Optional.of(account(UserStatus.SUSPENDED)));
        when(passwords.matches("clave", "hash")).thenReturn(true);

        assertThrows(ForbiddenOperationException.class, () -> authService.login("ana@test.com", "clave"));
        verify(tokens, never()).generate(any(), any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void cuenta_suspendida_con_clave_incorrecta_no_revela_su_estado() {
        when(users.findByEmail("ana@test.com")).thenReturn(Optional.of(account(UserStatus.SUSPENDED)));
        when(passwords.matches("mala", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login("ana@test.com", "mala"));
    }
}
