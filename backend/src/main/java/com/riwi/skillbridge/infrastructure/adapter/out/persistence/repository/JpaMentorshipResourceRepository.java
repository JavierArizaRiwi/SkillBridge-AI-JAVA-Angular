package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorshipResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaMentorshipResourceRepository extends JpaRepository<MentorshipResourceEntity, UUID> {
    List<MentorshipResourceEntity> findByOfferingIdOrderByCreatedAtAsc(UUID offeringId);
}
