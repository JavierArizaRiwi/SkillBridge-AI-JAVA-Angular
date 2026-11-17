package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import java.util.UUID;

/**
 * DTO returned by GET /api/ai/trending.
 * Contains aggregated analytics for a single offering over the last 14 days.
 *
 * offeringId       - UUID of the offering
 * name             - offering title (joined from offerings table)
 * trendScore       - composite score 0-100 indicating growth momentum
 * forecastNext7Days - estimated bookings in the next 7 days
 * recommendations  - total recommendations in the last 14 days
 * bookings         - total bookings in the last 14 days
 * conversionRate   - effectiveBookings / recommendations (0.0 – 1.0)
 * growthRate       - (recent7 - previous7) / previous7 bookings (-1.0 if no prior data)
 */
public record TrendingServiceResponse(
        UUID offeringId,
        String name,
        double trendScore,
        double forecastNext7Days,
        long recommendations,
        long bookings,
        double conversionRate,
        double growthRate
) {}
