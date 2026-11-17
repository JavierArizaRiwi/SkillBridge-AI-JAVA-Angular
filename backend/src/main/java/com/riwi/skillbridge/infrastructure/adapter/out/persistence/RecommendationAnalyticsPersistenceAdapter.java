package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.RecommendationAnalyticsRepositoryPort;
import com.riwi.skillbridge.domain.model.RecommendationDailyMetric;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.RecommendationDailyMetricEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaRecommendationDailyMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing RecommendationAnalyticsRepositoryPort.
 * Translates between domain models and JPA entities.
 * All write operations are transactional to ensure consistency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationAnalyticsPersistenceAdapter implements RecommendationAnalyticsRepositoryPort {

    private final JpaRecommendationDailyMetricRepository metricRepository;

    @Override
    @Transactional
    public void incrementRecommendations(UUID offeringId, LocalDate date) {
        RecommendationDailyMetricEntity entity = findOrCreate(offeringId, date);
        entity.setRecommendations(entity.getRecommendations() + 1);
        metricRepository.save(entity);
    }

    @Override
    @Transactional
    public void incrementBookings(UUID offeringId, LocalDate date) {
        RecommendationDailyMetricEntity entity = findOrCreate(offeringId, date);
        entity.setBookings(entity.getBookings() + 1);
        recomputeDerivedFields(entity);
        metricRepository.save(entity);
    }

    @Override
    @Transactional
    public void incrementCancellations(UUID offeringId, LocalDate date) {
        RecommendationDailyMetricEntity entity = findOrCreate(offeringId, date);
        entity.setCancellations(entity.getCancellations() + 1);
        recomputeDerivedFields(entity);
        metricRepository.save(entity);
    }

    @Override
    @Transactional
    public void registerUniqueUser(UUID offeringId, UUID userId, LocalDate date) {
        // Simple increment; true uniqueness per user/day is enforced at the consumer level
        RecommendationDailyMetricEntity entity = findOrCreate(offeringId, date);
        entity.setUniqueUsers(entity.getUniqueUsers() + 1);
        metricRepository.save(entity);
    }

    @Override
    public List<RecommendationDailyMetric> findMetricsBetween(LocalDate from, LocalDate to) {
        return metricRepository.findAllBetween(from, to)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RecommendationDailyMetric> findMetricsByOfferingBetween(UUID offeringId, LocalDate from, LocalDate to) {
        return metricRepository.findByOfferingBetween(offeringId, from, to)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<RecommendationDailyMetric> findByOfferingAndDate(UUID offeringId, LocalDate date) {
        return metricRepository.findByOfferingIdAndDate(offeringId, date)
                .map(this::toDomain);
    }

    // --- private helpers ---

    private RecommendationDailyMetricEntity findOrCreate(UUID offeringId, LocalDate date) {
        return metricRepository.findByOfferingIdAndDate(offeringId, date)
                .orElseGet(() -> {
                    log.debug("Creating new daily metric row for offering={} date={}", offeringId, date);
                    return new RecommendationDailyMetricEntity(
                            UUID.randomUUID(), date, offeringId,
                            0L, 0L, 0L, 0L, 0.0, 0.0, 0.0
                    );
                });
    }

    /**
     * Recomputes conversionRate after a booking or cancellation event.
     * conversionRate = effectiveBookings / recommendations  (0 if no recommendations)
     */
    private void recomputeDerivedFields(RecommendationDailyMetricEntity entity) {
        long effectiveBookings = Math.max(0L, entity.getBookings() - entity.getCancellations());
        double rate = entity.getRecommendations() > 0
                ? (double) effectiveBookings / entity.getRecommendations()
                : 0.0;
        entity.setConversionRate(rate);
    }

    private RecommendationDailyMetric toDomain(RecommendationDailyMetricEntity e) {
        return new RecommendationDailyMetric(
                e.getId(), e.getDate(), e.getOfferingId(),
                e.getRecommendations(), e.getUniqueUsers(),
                e.getBookings(), e.getCancellations(),
                e.getConversionRate(), e.getTrendScore(), e.getForecastNext7Days()
        );
    }
}
