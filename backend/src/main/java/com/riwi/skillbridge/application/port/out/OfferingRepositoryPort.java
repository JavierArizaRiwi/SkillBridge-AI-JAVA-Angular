package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingRepositoryPort {
    List<Offering> findAllActive();

    List<Offering> findAll();

    List<Offering> findByProviderId(UUID providerId);

    PageResult<Offering> findAllActive(PageQuery query);

    PageResult<Offering> findAll(PageQuery query);

    PageResult<Offering> findByProviderId(UUID providerId, PageQuery query);

    Optional<Offering> findById(UUID id);

    Offering save(Offering offering);
}
