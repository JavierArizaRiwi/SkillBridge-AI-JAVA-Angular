package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationSenderPort;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingStatusHistoryRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el flujo HTTP completo de cancelación contra PostgreSQL y RabbitMQ reales.
 * El control visual de doble clic corresponde al frontend y no se prueba aquí.
 */
@SpringBootTest(properties = {
    "app.jwt.secret=" + BookingCancellationIT.JWT_SECRET,
    "app.jwt.expiration-minutes=30",
    "app.cors.allowed-origins=http://localhost:4200",
    "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
    "spring.kafka.listener.auto-startup=false",
    "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(BookingCancellationIT.FixedClockConfiguration.class)
class BookingCancellationIT {

    static final String JWT_SECRET = "integration-test-secret-with-at-least-32-characters";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("b7d9f3c1-a8e4-4c59-b1d6-8f2a3e9c4b7d");
    private static final String OWNER_EMAIL = "user@gmail.com";
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String OTHER_CUSTOMER_EMAIL = "other-customer@test.com";


    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("skillbridge_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static final RabbitMQContainer RABBIT =
        new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.addresses",
            () -> "amqp://guest:guest@%s:%d".formatted(RABBIT.getHost(), RABBIT.getAmqpPort()));
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JpaBookingRepository bookingRepository;
    @Autowired
    private JpaBookingStatusHistoryRepository historyRepository;
    @Autowired
    private JpaUserRepository userRepository;
    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private NotificationSenderPort notificationSender;

    @MockitoBean
    private BookingEventPublisherPort bookingEventPublisher;

    @MockitoBean
    private AiRecommendationPort aiRecommendationPort;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.findById(OTHER_CUSTOMER_ID).ifPresent(userRepository::delete);
        userRepository.save(new UserEntity(OTHER_CUSTOMER_ID, "Other Customer", OTHER_CUSTOMER_EMAIL,
            passwordEncoder.encode("12345678"), Role.CUSTOMER, UserStatus.ACTIVE, NOW));
        amqpAdmin.purgeQueue(RabbitConfiguration.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfiguration.NOTIFICATION_DLQ);
        reset(notificationSender);
        reset(bookingEventPublisher);
    }

    @AfterEach
    void tearDown() {
        historyRepository.deleteAll();
        bookingRepository.deleteAll();
        userRepository.findById(OTHER_CUSTOMER_ID).ifPresent(userRepository::delete);
    }

    @Test
    void qa01_cancela_reserva_propia_y_persiste_historial_y_notificacion() throws Exception {
        UUID bookingId = persistBooking(OWNER_ID, NOW.plusSeconds(24 * 60 * 60));

        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", bearer(ownerToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(bookingId.toString()))
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
            assertThat(history.getBookingId()).isEqualTo(bookingId);
            assertThat(history.getPreviousStatus()).isEqualTo(BookingStatus.CREATED);
            assertThat(history.getNewStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(history.getChangedBy()).isEqualTo(OWNER_ID);
        });
        verify(notificationSender, timeout(5000).times(1)).send(argThat(message ->
            message.bookingId().equals(bookingId)
                && message.userId().equals(OWNER_ID)
                && message.notificationType() == NotificationType.BOOKING_CANCELLED));
        verify(bookingEventPublisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void qa02_reserva_ajena_responde_404_y_permanece_intacta() throws Exception {
        UUID bookingId = persistBooking(OWNER_ID, NOW.plusSeconds(48 * 60 * 60));

        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", bearer(otherCustomerToken())))
            .andExpect(status().isNotFound());

        assertUnchanged(bookingId);
        verify(notificationSender, never()).send(org.mockito.ArgumentMatchers.any());
        verify(bookingEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void qa03_reserva_inexistente_responde_404_sin_efectos_secundarios() throws Exception {
        mvc.perform(patch(cancelUrl(UUID.randomUUID())).header("Authorization", bearer(ownerToken())))
            .andExpect(status().isNotFound());

        assertThat(historyRepository.count()).isZero();
        verify(notificationSender, never()).send(org.mockito.ArgumentMatchers.any());
        verify(bookingEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void qa04_reserva_pasada_responde_422_y_permanece_intacta() throws Exception {
        UUID bookingId = persistBooking(OWNER_ID, NOW.minusSeconds(1));

        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", bearer(ownerToken())))
            .andExpect(status().isUnprocessableEntity());

        assertUnchanged(bookingId);
        verify(notificationSender, never()).send(org.mockito.ArgumentMatchers.any());
        verify(bookingEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void qa05_reserva_con_menos_de_24_horas_responde_422_y_el_limite_exacto_se_permite() throws Exception {
        UUID tooLate = persistBooking(OWNER_ID, NOW.plusSeconds(24 * 60 * 60 - 1));
        UUID exactLimit = persistBooking(OWNER_ID, NOW.plusSeconds(24 * 60 * 60));

        mvc.perform(patch(cancelUrl(tooLate)).header("Authorization", bearer(ownerToken())))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(patch(cancelUrl(exactLimit)).header("Authorization", bearer(ownerToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(bookingRepository.findById(tooLate).orElseThrow().getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(bookingRepository.findById(exactLimit).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(historyRepository.findAll()).singleElement().satisfies(history ->
            assertThat(history.getBookingId()).isEqualTo(exactLimit));
        verify(notificationSender, timeout(5000).times(1)).send(argThat(message ->
            message.bookingId().equals(exactLimit) && message.notificationType() == NotificationType.BOOKING_CANCELLED));
        verify(bookingEventPublisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void qa06_segunda_cancelacion_es_idempotente_y_no_duplica_efectos() throws Exception {
        UUID bookingId = persistBooking(OWNER_ID, NOW.plusSeconds(48 * 60 * 60));

        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", bearer(ownerToken())))
            .andExpect(status().isOk());
        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", bearer(ownerToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(historyRepository.count()).isEqualTo(1);
        verify(notificationSender, timeout(5000).times(1)).send(argThat(message ->
            message.bookingId().equals(bookingId) && message.notificationType() == NotificationType.BOOKING_CANCELLED));
        verify(bookingEventPublisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void qa07_jwt_ausente_expirado_alterado_o_malformado_responde_401_sin_efectos() throws Exception {
        UUID bookingId = persistBooking(OWNER_ID, NOW.plusSeconds(48 * 60 * 60));

        mvc.perform(patch(cancelUrl(bookingId))).andExpect(status().isUnauthorized());
        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", bearer(expiredOwnerToken())))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", bearer(ownerToken() + "altered")))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", "Basic invalid"))
            .andExpect(status().isUnauthorized());

        assertUnchanged(bookingId);
        verify(notificationSender, never()).send(org.mockito.ArgumentMatchers.any());
        verify(bookingEventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void qa08_dos_cancelaciones_simultaneas_producen_una_sola_transicion() throws Exception {
        UUID bookingId = persistBooking(OWNER_ID, NOW.plusSeconds(48 * 60 * 60));
        String authorization = bearer(ownerToken());
        CyclicBarrier startGate = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Integer> cancel = () -> {
                startGate.await();
                return mvc.perform(patch(cancelUrl(bookingId)).header("Authorization", authorization))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            };
            List<Future<Integer>> responses = executor.invokeAll(List.of(cancel, cancel));

            assertThat(responses.get(0).get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(responses.get(1).get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(historyRepository.count()).isEqualTo(1);
        verify(notificationSender, timeout(5000).times(1)).send(argThat(message ->
            message.bookingId().equals(bookingId) && message.notificationType() == NotificationType.BOOKING_CANCELLED));
        verify(bookingEventPublisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    private UUID persistBooking(UUID customerId, Instant scheduledAt) {
        UUID bookingId = UUID.randomUUID();
        bookingRepository.saveAndFlush(new BookingEntity(bookingId, OFFERING_ID, customerId,
            scheduledAt, BookingStatus.CREATED, 0, NOW));
        return bookingId;
    }

    private void assertUnchanged(UUID bookingId) {
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(historyRepository.count()).isZero();
    }

    private String ownerToken() {
        return jwtService.generate(OWNER_EMAIL, Role.CUSTOMER.name());
    }

    private String otherCustomerToken() {
        return jwtService.generate(OTHER_CUSTOMER_EMAIL, Role.CUSTOMER.name());
    }

    private String expiredOwnerToken() {
        return Jwts.builder()
            .subject(OWNER_EMAIL)
            .issuedAt(Date.from(Instant.parse("2000-01-01T00:00:00Z")))
            .expiration(Date.from(Instant.parse("2000-01-01T00:01:00Z")))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private String cancelUrl(UUID bookingId) {
        return "/api/bookings/" + bookingId + "/cancel";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

