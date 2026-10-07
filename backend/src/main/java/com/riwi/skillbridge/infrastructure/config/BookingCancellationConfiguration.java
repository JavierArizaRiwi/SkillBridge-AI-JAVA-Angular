package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.domain.service.BookingCancellationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class BookingCancellationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BookingCancellationPolicy bookingCancellationPolicy(
            Clock clock,
            @Value("${app.booking.cancellation.minimum-notice-hours}") long minimumNoticeHours) {
        return new BookingCancellationPolicy(clock, Duration.ofHours(minimumNoticeHours));
    }
}
