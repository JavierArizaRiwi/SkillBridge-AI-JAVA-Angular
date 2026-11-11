package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.RecommendationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for recommendation event records.
 * existsById(UUID) is inherited from JpaRepository and used by the analytics consumer
 * to check idempotency: if the eventId already exists, the duplicate message is ignored.
 */
public interface JpaRecommendationEventRepository extends JpaRepository<RecommendationEventEntity, UUID> {
}
