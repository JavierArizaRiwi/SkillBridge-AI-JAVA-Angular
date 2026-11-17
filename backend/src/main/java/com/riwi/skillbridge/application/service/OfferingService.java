package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;

import java.util.List;

@Service
public class OfferingService implements ListOfferingsUseCase {
    private final OfferingRepositoryPort repository;
    private final OfferingCachePort cache;

    public OfferingService(OfferingRepositoryPort repository, OfferingCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public List<Offering> listActive() {
        return cache.getActiveOfferings().orElseGet(() -> {
            List<Offering> offerings = repository.findAllActive();
            cache.putActiveOfferings(offerings);
            return offerings;
        });
    }

    @Override
    public PageResult<Offering> listActive(PageQuery query) {
        return repository.findAllActive(query);
    }
}
