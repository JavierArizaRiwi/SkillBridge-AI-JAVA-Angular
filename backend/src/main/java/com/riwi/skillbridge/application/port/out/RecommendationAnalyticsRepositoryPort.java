package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.RecommendationDailyMetric;
import com.riwi.skillbridge.domain.model.RecommendationInputType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for persisting and querying recommendation analytics data.
 * This port is used by the analytics consumer to aggregate event data
 * and by the trending use case to query computed metrics.
 */
public interface RecommendationAnalyticsRepositoryPort {

    /**
     * Increments the recommendation count for a given offering on a given date.
     * Creates the metric row if it does not exist (upsert semantics).
     */
    void incrementRecommendations(UUID offeringId, LocalDate date);

    /**
     * Increments the booking count for a given offering on a given date.
     */
    void incrementBookings(UUID offeringId, LocalDate date);

    /**
     * Increments the cancellation count for a given offering on a given date.
     */
    void incrementCancellations(UUID offeringId, LocalDate date);

    /**
     * Registers a unique user interaction for a given offering on a given date.
     * Only counts the user once per day per offering.
     */
    void registerUniqueUser(UUID offeringId, UUID userId, LocalDate date);

    /**
     * Returns the aggregated daily metrics for all offerings within a date range.
     */
    List<RecommendationDailyMetric> findMetricsBetween(LocalDate from, LocalDate to);

    /**
     * Returns the daily metrics for a specific offering within a date range.
     */
    List<RecommendationDailyMetric> findMetricsByOfferingBetween(UUID offeringId, LocalDate from, LocalDate to);

    /**
     * Returns the most recent metric record for a given offering and date, if any.
     */
    Optional<RecommendationDailyMetric> findByOfferingAndDate(UUID offeringId, LocalDate date);
}
