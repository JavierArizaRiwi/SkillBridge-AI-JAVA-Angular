package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingRepositoryPort {
    List<Offering> findAllActive();

    List<Offering> findAll();

    List<Offering> findByProviderId(UUID providerId);

    Optional<Offering> findById(UUID id);

    Offering save(Offering offering);
}
