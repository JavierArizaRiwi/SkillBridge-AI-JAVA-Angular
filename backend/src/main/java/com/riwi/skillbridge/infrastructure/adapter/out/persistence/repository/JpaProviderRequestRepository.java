package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ProviderRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaProviderRequestRepository extends JpaRepository<ProviderRequestEntity, UUID> {
    List<ProviderRequestEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<ProviderRequestEntity> findByStatus(ProviderRequestStatus status, Pageable pageable);
}
