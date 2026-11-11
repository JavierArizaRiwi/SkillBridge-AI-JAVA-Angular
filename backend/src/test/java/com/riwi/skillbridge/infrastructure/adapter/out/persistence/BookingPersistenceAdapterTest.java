package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.BookingStatusHistory;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingStatusHistoryRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingPersistenceAdapterTest {
    private static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("b7d9f3c1-a8e4-4c59-b1d6-8f2a3e9c4b7d");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("skillbridge_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JpaBookingRepository bookingRepository;
    @Autowired
    JpaUserRepository userRepository;
    @Autowired
    JpaBookingStatusHistoryRepository historyRepository;

    @Test
    void shouldPersistCancellationHistoryAndPreserveBookingCreationData() {
        BookingPersistenceAdapter bookingAdapter = new BookingPersistenceAdapter(bookingRepository, userRepository);
        BookingStatusHistoryPersistenceAdapter historyAdapter = new BookingStatusHistoryPersistenceAdapter(historyRepository);
        UUID bookingId = UUID.randomUUID();
        Booking created = new Booking(
                bookingId,
                OFFERING_ID,
                CUSTOMER_ID,
                Instant.parse("2030-10-12T12:00:00Z"),
                BookingStatus.CREATED,
                0);

        Booking persisted = bookingAdapter.save(created);
        BookingEntity beforeCancellation = bookingRepository.findById(bookingId).orElseThrow();
        Instant createdAt = beforeCancellation.getCreatedAt();

        Booking cancelled = bookingAdapter.save(persisted.cancel());
        historyAdapter.save(BookingStatusHistory.forTransition(
                persisted,
                cancelled.status(),
                CUSTOMER_ID,
                Instant.parse("2030-10-10T12:00:00Z")));

        BookingEntity afterCancellation = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.version()).isEqualTo(1);
        assertThat(afterCancellation.getCreatedAt()).isEqualTo(createdAt);
        assertThat(afterCancellation.getVersion()).isEqualTo(1);
        assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
            assertThat(history.getBookingId()).isEqualTo(bookingId);
            assertThat(history.getPreviousStatus()).isEqualTo(BookingStatus.CREATED);
            assertThat(history.getNewStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(history.getChangedBy()).isEqualTo(CUSTOMER_ID);
        });
    }
}
