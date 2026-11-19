package com.riwi.skillbridge.infrastructure.adapter.out.notification;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.PermanentNotificationException;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private UserRepositoryPort userRepository;

    private EmailNotificationSender emailSender;
    private final String senderEmail = "no-reply@skillbridge.local";

    @BeforeEach
    void setUp() {
        emailSender = new EmailNotificationSender(mailSender, userRepository, senderEmail);
    }

    @Test
    @DisplayName("Debe armar destinatario, asunto y cuerpo correctamente al enviar un correo")
    void shouldBuildAndSendEmailSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        NotificationMessage message = NotificationMessage.bookingCreated(bookingId, userId);

        UserAccount user = new UserAccount(userId, "Juan Perez", "juan@example.com", "pass", Role.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        emailSender.send(message);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMail = messageCaptor.getValue();
        assertThat(sentMail.getFrom()).isEqualTo(senderEmail);
        assertThat(sentMail.getTo()).containsExactly("juan@example.com");
        assertThat(sentMail.getSubject()).isEqualTo("Reserva creada");
        assertThat(sentMail.getText()).contains("Hola Juan Perez, tu reserva " + bookingId + " fue creada.");
    }

    @Test
    @DisplayName("Debe lanzar PermanentNotificationException si el usuario no existe")
    void shouldThrowPermanentExceptionWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), userId);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailSender.send(message))
            .isInstanceOf(PermanentNotificationException.class)
            .hasMessageContaining("Usuario no encontrado");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Debe lanzar PermanentNotificationException si el usuario no tiene correo")
    void shouldThrowPermanentExceptionWhenUserEmailIsBlank() {
        UUID userId = UUID.randomUUID();
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), userId);

        UserAccount user = new UserAccount(userId, "Juan Perez", "", "pass", Role.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> emailSender.send(message))
            .isInstanceOf(PermanentNotificationException.class)
            .hasMessageContaining("Usuario sin correo");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Debe propagar la excepcion del SMTP para que actue el retry de RabbitMQ")
    void shouldPropagateMailExceptionWhenSmtpFails() {
        UUID userId = UUID.randomUUID();
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), userId);

        UserAccount user = new UserAccount(userId, "Juan Perez", "juan@example.com", "pass", Role.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        doThrow(new MailSendException("SMTP connection refused"))
            .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailSender.send(message))
            .isInstanceOf(MailSendException.class);
    }
}
