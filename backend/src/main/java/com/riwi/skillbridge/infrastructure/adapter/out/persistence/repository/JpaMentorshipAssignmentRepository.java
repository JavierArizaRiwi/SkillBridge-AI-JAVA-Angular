package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorshipAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JpaMentorshipAssignmentRepository extends JpaRepository<MentorshipAssignmentEntity, UUID> {
    Optional<MentorshipAssignmentEntity> findByBookingId(UUID bookingId);
    List<MentorshipAssignmentEntity> findByCustomerIdOrderByAccessStartsAtDesc(UUID customerId);
}
