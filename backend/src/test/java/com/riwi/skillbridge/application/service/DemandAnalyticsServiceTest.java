package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.RecommendationAnalyticsRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.RecommendationDailyMetric;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.TrendingServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DemandAnalyticsService.
 *
 * Strategy: test each formula method in isolation (pure functions),
 * then test getTrending() with mocked repositories to validate
 * the full aggregation pipeline without a database.
 */
class DemandAnalyticsServiceTest {

    private DemandAnalyticsService service;
    private RecommendationAnalyticsRepositoryPort analyticsRepository;
    private OfferingRepositoryPort offeringRepository;

    @BeforeEach
    void setUp() {
        analyticsRepository = mock(RecommendationAnalyticsRepositoryPort.class);
        offeringRepository  = mock(OfferingRepositoryPort.class);
        service = new DemandAnalyticsService(analyticsRepository, offeringRepository);
    }

    // ── conversionRate ────────────────────────────────────────────────────────

    @Nested
    class ConversionRateTests {

        @Test
        void shouldReturnZeroWhenNoRecommendations() {
            // If no one was ever recommended an offering, conversion is 0 — not infinity
            double result = service.conversionRate(5, 0);
            assertEquals(0.0, result);
        }

        @Test
        void shouldComputeCorrectRate() {
            // 10 bookings out of 40 recommendations → 0.25
            double result = service.conversionRate(10, 40);
            assertEquals(0.25, result, 0.0001);
        }

        @Test
        void shouldCapAtOneWhenBookingsExceedRecommendations() {
            // Edge case: more bookings than recommendations (e.g. direct bookings)
            double result = service.conversionRate(20, 5);
            assertEquals(1.0, result, 0.0001);
        }

        @Test
        void shouldReturnZeroWhenBothAreZero() {
            double result = service.conversionRate(0, 0);
            assertEquals(0.0, result);
        }
    }

    // ── growthRate ────────────────────────────────────────────────────────────

    @Nested
    class GrowthRateTests {

        @Test
        void shouldReturnZeroWhenNoPreviousBookings() {
            // New offering with no history — growth is unknown, not infinite
            double result = service.growthRate(10, 0);
            assertEquals(0.0, result);
        }

        @Test
        void shouldComputePositiveGrowth() {
            // 15 recent vs 10 previous → +50% growth
            double result = service.growthRate(15, 10);
            assertEquals(0.5, result, 0.0001);
        }

        @Test
        void shouldComputeNegativeGrowth() {
            // 5 recent vs 10 previous → -50% decline
            double result = service.growthRate(5, 10);
            assertEquals(-0.5, result, 0.0001);
        }

        @Test
        void shouldReturnZeroWhenNoChangeInDemand() {
            double result = service.growthRate(10, 10);
            assertEquals(0.0, result, 0.0001);
        }
    }

    // ── trendScore ────────────────────────────────────────────────────────────

    @Nested
    class TrendScoreTests {

        @Test
        void shouldReturnZeroForOfferingWithNoActivity() {
            double score = service.trendScore(0.0, 0.0, 0, 1);
            assertEquals(0.0, score, 0.0001);
        }

        @Test
        void shouldReturnMaxScoreForPerfectOffering() {
            // 100% conversion, 200% growth, max recommendations
            double score = service.trendScore(1.0, 2.0, 100, 100);
            assertEquals(100.0, score, 0.0001);
        }

        @Test
        void shouldNeverExceed100() {
            double score = service.trendScore(1.0, 5.0, 1000, 1);
            assertTrue(score <= 100.0, "trendScore must never exceed 100");
        }

        @Test
        void shouldNeverBeNegative() {
            // Worst case: no conversion, maximum decline, no recommendations
            double score = service.trendScore(0.0, -1.0, 0, 100);
            assertTrue(score >= 0.0, "trendScore must never be negative");
        }

        @Test
        void shouldWeighConversionMost() {
            // An offering with 100% conversion but no growth should outscore
            // an offering with no conversion but high growth
            double highConversion = service.trendScore(1.0, 0.0, 10, 100);
            double highGrowth     = service.trendScore(0.0, 2.0, 10, 100);
            assertTrue(highConversion > highGrowth,
                    "Conversion (50% weight) should outweigh growth (30% weight)");
        }
    }

    // ── forecastNext7Days ─────────────────────────────────────────────────────

    @Nested
    class ForecastTests {

        @Test
        void shouldReturnZeroWhenNoRecentBookings() {
            double forecast = service.forecastNext7Days(0, 0.0);
            assertEquals(0.0, forecast, 0.0001);
        }

        @Test
        void shouldForecastSameVolumeWhenNoGrowth() {
            // 7 bookings in 7 days, 0% growth → forecast = 7
            double forecast = service.forecastNext7Days(7, 0.0);
            assertEquals(7.0, forecast, 0.0001);
        }

        @Test
        void shouldAmplifiyForecastWithPositiveGrowth() {
            // 7 bookings, 100% growth → forecast = 14
            double forecast = service.forecastNext7Days(7, 1.0);
            assertEquals(14.0, forecast, 0.0001);
        }

        @Test
        void shouldNotPenalizeForecastWithNegativeGrowth() {
            // Negative growth does NOT reduce forecast below the flat projection
            // because max(0, growthRate) floors the amplifier at 1.0
            double forecast = service.forecastNext7Days(7, -0.5);
            assertEquals(7.0, forecast, 0.0001);
        }
    }

    // ── getTrending() full pipeline ───────────────────────────────────────────

    @Nested
    class GetTrendingTests {

        @Test
        void shouldReturnEmptyListWhenNoAnalyticsData() {
            when(analyticsRepository.findMetricsBetween(any(), any())).thenReturn(List.of());
            when(offeringRepository.findAllActive()).thenReturn(List.of());

            List<TrendingServiceResponse> result = service.getTrending();

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnTrendingOfferingsSortedByTrendScoreDescending() {
            UUID offeringA = UUID.randomUUID();
            UUID offeringB = UUID.randomUUID();
            LocalDate today = LocalDate.now();

            // Offering A: high conversion (10 bookings, 20 recs)
            // Offering B: low conversion (1 booking, 20 recs)
            List<RecommendationDailyMetric> metrics = List.of(
                    metric(offeringA, today.minusDays(1), 20, 10, 0),
                    metric(offeringB, today.minusDays(1), 20,  1, 0)
            );

            when(analyticsRepository.findMetricsBetween(any(), any())).thenReturn(metrics);
            when(offeringRepository.findAllActive()).thenReturn(List.of(
                    offering(offeringA, "Mentoría Java"),
                    offering(offeringB, "Mentoría Angular")
            ));

            List<TrendingServiceResponse> result = service.getTrending();

            assertEquals(2, result.size());
            assertEquals(offeringA, result.get(0).offeringId(),
                    "Offering A with higher conversion should rank first");
            assertTrue(result.get(0).trendScore() >= result.get(1).trendScore(),
                    "Results must be sorted descending by trendScore");
        }

        @Test
        void shouldExcludeOfferingsWithNoRecentActivity() {
            UUID offeringOld = UUID.randomUUID();
            LocalDate today  = LocalDate.now();

            // This metric is older than 7 days (falls in previous window, not recent)
            List<RecommendationDailyMetric> metrics = List.of(
                    metric(offeringOld, today.minusDays(10), 5, 2, 0)
            );

            when(analyticsRepository.findMetricsBetween(any(), any())).thenReturn(metrics);
            when(offeringRepository.findAllActive()).thenReturn(List.of(
                    offering(offeringOld, "Servicio antiguo")
            ));

            List<TrendingServiceResponse> result = service.getTrending();

            assertTrue(result.isEmpty(),
                    "Offerings with no activity in the recent 7-day window should be excluded");
        }

        @Test
        void shouldIncludeOfferingNameFromOfferingRepository() {
            UUID offeringId = UUID.randomUUID();
            LocalDate today = LocalDate.now();

            when(analyticsRepository.findMetricsBetween(any(), any())).thenReturn(List.of(
                    metric(offeringId, today.minusDays(1), 10, 3, 0)
            ));
            when(offeringRepository.findAllActive()).thenReturn(List.of(
                    offering(offeringId, "Diseño Cloud")
            ));

            List<TrendingServiceResponse> result = service.getTrending();

            assertEquals("Diseño Cloud", result.get(0).name());
        }

        @Test
        void shouldHandleCancellationsAndReduceEffectiveBookings() {
            UUID offeringId = UUID.randomUUID();
            LocalDate today = LocalDate.now();

            // 10 bookings but 4 cancellations → 6 effective bookings
            when(analyticsRepository.findMetricsBetween(any(), any())).thenReturn(List.of(
                    metric(offeringId, today.minusDays(1), 20, 10, 4)
            ));
            when(offeringRepository.findAllActive()).thenReturn(List.of(
                    offering(offeringId, "Test Offering")
            ));

            List<TrendingServiceResponse> result = service.getTrending();

            assertEquals(1, result.size());
            // conversionRate = 6 effective / 20 recs = 0.30
            assertEquals(0.30, result.get(0).conversionRate(), 0.01);
        }
    }

    // ── test data helpers ─────────────────────────────────────────────────────

    private RecommendationDailyMetric metric(UUID offeringId, LocalDate date,
                                              long recommendations, long bookings,
                                              long cancellations) {
        long effectiveBookings = Math.max(0, bookings - cancellations);
        double conversionRate  = recommendations > 0
                ? (double) effectiveBookings / recommendations : 0.0;
        return new RecommendationDailyMetric(
                UUID.randomUUID(), date, offeringId,
                recommendations, 1L, bookings, cancellations,
                conversionRate, 0.0, 0.0
        );
    }

    private Offering offering(UUID id, String title) {
        return new Offering(id, UUID.randomUUID(), title, "description", "BACKEND",
                BigDecimal.valueOf(100), true);
    }
}
