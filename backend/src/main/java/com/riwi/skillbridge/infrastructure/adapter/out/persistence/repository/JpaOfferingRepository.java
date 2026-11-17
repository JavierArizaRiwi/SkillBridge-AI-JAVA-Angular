package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface JpaOfferingRepository extends JpaRepository<OfferingEntity, UUID> {
    List<OfferingEntity> findByActiveTrueOrderByTitleAsc();
    List<OfferingEntity> findAllByOrderByTitleAsc();
    List<OfferingEntity> findByProviderIdOrderByTitleAsc(UUID providerId);
    Page<OfferingEntity> findByActiveTrue(Pageable pageable);
    Page<OfferingEntity> findByProviderId(UUID providerId, Pageable pageable);
}
