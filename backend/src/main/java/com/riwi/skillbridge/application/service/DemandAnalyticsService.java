package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.GetTrendingServicesUseCase;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.RecommendationAnalyticsRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.RecommendationDailyMetric;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.TrendingServiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service implementing GetTrendingServicesUseCase.
 *
 * FORMULA DOCUMENTATION
 * ─────────────────────
 * All metrics are computed over two windows derived from the last 14 days:
 *   recent   = last  7 days  (today-6 to today)
 *   previous = prior 7 days  (today-13 to today-7)
 *
 * conversionRate
 *   = effectiveBookings(recent) / recommendations(recent)
 *   = 0.0 if recommendations == 0
 *   Range: [0.0, 1.0]
 *
 * growthRate
 *   = (bookings_recent - bookings_previous) / bookings_previous
 *   = 0.0 if bookings_previous == 0
 *   Range: uncapped, typically [-1.0, +∞)
 *
 * trendScore  (composite 0–100)
 *   = 50 * conversionRate
 *   + 30 * clamp(growthRate, -1, 2) normalized to [0,1]  → ((growthRate+1)/3)*100 * 0.30
 *   + 20 * (recommendations_recent / maxRecommendations)
 *   Range: [0.0, 100.0]
 *
 * forecastNext7Days
 *   = avgDailyEffectiveBookings(recent) * 7 * amplifier
 *   amplifier = 1 + max(0, growthRate)   — only positive growth amplifies the forecast
 *   Range: [0.0, +∞)
 *
 * Handling insufficient data:
 *   - Offerings with zero recommendations AND zero bookings in the last 14 days are excluded.
 *   - Division by zero is always guarded with explicit checks returning 0.0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandAnalyticsService implements GetTrendingServicesUseCase {

    private static final int WINDOW_DAYS = 7;

    private final RecommendationAnalyticsRepositoryPort analyticsRepository;
    private final OfferingRepositoryPort offeringRepository;

    @Override
    public List<TrendingServiceResponse> getTrending() {
        LocalDate today = LocalDate.now();
        LocalDate recentStart    = today.minusDays(WINDOW_DAYS - 1);      // today-6
        LocalDate previousStart  = today.minusDays(WINDOW_DAYS * 2 - 1); // today-13
        LocalDate previousEnd    = today.minusDays(WINDOW_DAYS);          // today-7

        // Load all metrics for the full 14-day window
        List<RecommendationDailyMetric> allMetrics =
                analyticsRepository.findMetricsBetween(previousStart, today);

        if (allMetrics.isEmpty()) {
            log.info("No analytics data available for trending query");
            return List.of();
        }

        // Group metrics by offeringId
        Map<UUID, List<RecommendationDailyMetric>> byOffering = allMetrics.stream()
                .collect(Collectors.groupingBy(RecommendationDailyMetric::offeringId));

        // Load offering names for display
        Map<UUID, String> offeringNames = offeringRepository.findAllActive().stream()
                .collect(Collectors.toMap(Offering::id, Offering::title));

        // Find the max recommendations in recent window (for normalization in trendScore)
        long maxRecommendations = byOffering.values().stream()
                .mapToLong(metrics -> sumRecommendations(metrics, recentStart, today))
                .max()
                .orElse(1L);

        List<TrendingServiceResponse> result = new ArrayList<>();

        for (Map.Entry<UUID, List<RecommendationDailyMetric>> entry : byOffering.entrySet()) {
            UUID offeringId = entry.getKey();
            List<RecommendationDailyMetric> metrics = entry.getValue();

            // Split into recent and previous windows
            long recentRecs      = sumRecommendations(metrics, recentStart, today);
            long recentBookings  = sumEffectiveBookings(metrics, recentStart, today);
            long previousBookings = sumEffectiveBookings(metrics, previousStart, previousEnd);
            long recentRawBookings = sumRawBookings(metrics, recentStart, today);

            // Skip offerings with no activity in the recent window
            if (recentRecs == 0 && recentBookings == 0) continue;

            double conversionRate = conversionRate(recentBookings, recentRecs);
            double growthRate     = growthRate(recentBookings, previousBookings);
            double trendScore     = trendScore(conversionRate, growthRate, recentRecs, maxRecommendations);
            double forecast       = forecastNext7Days(recentBookings, growthRate);

            String name = offeringNames.getOrDefault(offeringId, offeringId.toString());

            result.add(new TrendingServiceResponse(
                    offeringId,
                    name,
                    round(trendScore),
                    round(forecast),
                    recentRecs,
                    recentRawBookings,
                    round(conversionRate),
                    round(growthRate)
            ));
        }

        // Sort by trendScore descending
        result.sort(Comparator.comparingDouble(TrendingServiceResponse::trendScore).reversed());

        log.info("Trending query returned {} offerings", result.size());
        return result;
    }

    // ── Formulas ─────────────────────────────────────────────────────────────

    /**
     * conversionRate = effectiveBookings / recommendations
     * Returns 0.0 if no recommendations exist (avoids division by zero).
     */
    double conversionRate(long effectiveBookings, long recommendations) {
        if (recommendations == 0) return 0.0;
        return Math.min(1.0, (double) effectiveBookings / recommendations);
    }

    /**
     * growthRate = (recent - previous) / previous
     * Returns 0.0 if previous == 0 (new offering with no history).
     */
    double growthRate(long recentBookings, long previousBookings) {
        if (previousBookings == 0) return 0.0;
        return (double) (recentBookings - previousBookings) / previousBookings;
    }

    /**
     * trendScore composite (0–100):
     *   50% conversion signal
     *   30% growth signal     (growthRate clamped to [-1,+2], then normalized to [0,1])
     *   20% volume signal     (recommendations / maxRecommendations)
     */
    double trendScore(double conversionRate, double growthRate, long recommendations, long maxRecommendations) {
        if (conversionRate == 0.0 && growthRate == 0.0 && recommendations == 0) {
            return 0.0;
        }

        double conversionSignal = conversionRate * 100.0 * 0.50;

        double clampedGrowth = Math.max(-1.0, Math.min(2.0, growthRate));
        double growthNormalized = (clampedGrowth + 1.0) / 3.0; // maps [-1,2] → [0,1]
        double growthSignal = growthNormalized * 100.0 * 0.30;

        double volumeRatio = maxRecommendations > 0
                ? (double) recommendations / maxRecommendations
                : 0.0;
        double volumeSignal = volumeRatio * 100.0 * 0.20;

        return Math.min(100.0, conversionSignal + growthSignal + volumeSignal);
    }

    /**
     * forecastNext7Days = avgDailyBookings * 7 * amplifier
     * amplifier = 1 + max(0, growthRate)  — only positive growth amplifies
     */
    double forecastNext7Days(long recentEffectiveBookings, double growthRate) {
        double avgDaily = (double) recentEffectiveBookings / WINDOW_DAYS;
        double amplifier = 1.0 + Math.max(0.0, growthRate);
        return avgDaily * WINDOW_DAYS * amplifier;
    }

    // ── Aggregation helpers ───────────────────────────────────────────────────

    private long sumRecommendations(List<RecommendationDailyMetric> metrics,
                                    LocalDate from, LocalDate to) {
        return metrics.stream()
                .filter(m -> !m.date().isBefore(from) && !m.date().isAfter(to))
                .mapToLong(RecommendationDailyMetric::recommendations)
                .sum();
    }

    private long sumEffectiveBookings(List<RecommendationDailyMetric> metrics,
                                      LocalDate from, LocalDate to) {
        return metrics.stream()
                .filter(m -> !m.date().isBefore(from) && !m.date().isAfter(to))
                .mapToLong(RecommendationDailyMetric::effectiveBookings)
                .sum();
    }

    private long sumRawBookings(List<RecommendationDailyMetric> metrics,
                                LocalDate from, LocalDate to) {
        return metrics.stream()
                .filter(m -> !m.date().isBefore(from) && !m.date().isAfter(to))
                .mapToLong(RecommendationDailyMetric::bookings)
                .sum();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
