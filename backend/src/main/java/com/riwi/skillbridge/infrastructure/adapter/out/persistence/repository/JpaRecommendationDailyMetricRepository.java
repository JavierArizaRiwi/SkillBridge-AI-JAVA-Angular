package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.RecommendationDailyMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for daily aggregated analytics metrics.
 */
public interface JpaRecommendationDailyMetricRepository extends JpaRepository<RecommendationDailyMetricEntity, UUID> {

    Optional<RecommendationDailyMetricEntity> findByOfferingIdAndDate(UUID offeringId, LocalDate date);

    @Query("SELECT m FROM RecommendationDailyMetricEntity m WHERE m.date BETWEEN :from AND :to ORDER BY m.date ASC")
    List<RecommendationDailyMetricEntity> findAllBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT m FROM RecommendationDailyMetricEntity m WHERE m.offeringId = :offeringId AND m.date BETWEEN :from AND :to ORDER BY m.date ASC")
    List<RecommendationDailyMetricEntity> findByOfferingBetween(
            @Param("offeringId") UUID offeringId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Returns the distinct offering IDs that have at least one metric record in the date range.
     * Used by the trending service to identify which offerings to compute scores for.
     */
    @Query("SELECT DISTINCT m.offeringId FROM RecommendationDailyMetricEntity m WHERE m.date BETWEEN :from AND :to")
    List<UUID> findDistinctOfferingIdsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
