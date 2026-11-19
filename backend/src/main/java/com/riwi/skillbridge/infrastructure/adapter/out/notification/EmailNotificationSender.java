package com.riwi.skillbridge.infrastructure.adapter.out.notification;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationSenderPort;
import com.riwi.skillbridge.application.port.out.PermanentNotificationException;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.sender", havingValue = "email")
public class EmailNotificationSender implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final UserRepositoryPort users;
    private final String from;

    public EmailNotificationSender(JavaMailSender mailSender,
                                   UserRepositoryPort users,
                                   @Value("${notifications.email.from}") String from) {
        this.mailSender = mailSender;
        this.users = users;
        this.from = from;
    }

    @Override
    public void send(NotificationMessage message) {
        UserAccount user = users.findById(message.userId())
            .orElseThrow(() -> new PermanentNotificationException(
                "Usuario no encontrado para eventId=" + message.eventId()));
        if (user.email() == null || user.email().isBlank()) {
            throw new PermanentNotificationException(
                "Usuario sin correo para eventId=" + message.eventId());
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(user.email());
        mail.setSubject(subject(message));
        mail.setText(body(user, message));

        // Un fallo del SMTP lanza MailException: se propaga para que actúen retry y DLQ
        mailSender.send(mail);
        log.info("Email sent type={} bookingId={} eventId={}",
            message.notificationType(), message.bookingId(), message.eventId());
    }

    private String subject(NotificationMessage message) {
        return switch (message.notificationType()) {
            case BOOKING_CREATED -> "Reserva creada";
            case BOOKING_CANCELLED -> "Reserva cancelada";
        };
    }

    private String body(UserAccount user, NotificationMessage message) {
        String action = switch (message.notificationType()) {
            case BOOKING_CREATED -> "fue creada";
            case BOOKING_CANCELLED -> "fue cancelada";
        };
        return "Hola " + user.name() + ", tu reserva " + message.bookingId() + " " + action + ".";
    }
}
