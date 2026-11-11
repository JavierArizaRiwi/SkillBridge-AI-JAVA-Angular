package com.riwi.skillbridge.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain model representing the aggregated analytics for a single offering on a single day.
 * This record is immutable and belongs to the domain layer — no JPA annotations here.
 *
 * Fields:
 *   id              - surrogate key (UUID)
 *   date            - the calendar day this metric covers
 *   offeringId      - the service being measured
 *   recommendations - how many times this offering appeared in AI recommendations
 *   uniqueUsers     - distinct users who received a recommendation for this offering
 *   bookings        - bookings created for this offering on this day
 *   cancellations   - bookings cancelled for this offering on this day
 *   conversionRate  - bookings / recommendations (0 if no recommendations)
 *   trendScore      - composite score: see DemandAnalyticsService for formula
 *   forecastNext7Days - estimated bookings in the next 7 days
 */
public record RecommendationDailyMetric(
        UUID id,
        LocalDate date,
        UUID offeringId,
        long recommendations,
        long uniqueUsers,
        long bookings,
        long cancellations,
        double conversionRate,
        double trendScore,
        double forecastNext7Days
) {
    /**
     * Creates a new empty metric record for a given offering and date.
     * Used when a metric row does not yet exist for this offering/date combination.
     */
    public static RecommendationDailyMetric empty(UUID offeringId, LocalDate date) {
        return new RecommendationDailyMetric(
                UUID.randomUUID(), date, offeringId,
                0L, 0L, 0L, 0L,
                0.0, 0.0, 0.0
        );
    }

    /** Returns effective bookings: bookings minus cancellations (floor at 0). */
    public long effectiveBookings() {
        return Math.max(0L, bookings - cancellations);
    }
}
