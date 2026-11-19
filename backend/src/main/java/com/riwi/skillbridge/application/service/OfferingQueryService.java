package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ListAllOfferingsUseCase;
import com.riwi.skillbridge.application.port.in.ListMyOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Role;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OfferingQueryService implements ListMyOfferingsUseCase, ListAllOfferingsUseCase {
    private final OfferingRepositoryPort repository;

    public OfferingQueryService(OfferingRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<Offering> listMine(Actor actor) {
        if (actor.role() != Role.PROVIDER) {
            throw new ForbiddenOperationException("Solo un Provider tiene offerings propios");
        }
        return repository.findByProviderId(actor.id());
    }

    @Override
    public List<Offering> listAll(Actor actor) {
        if (actor.role() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un Admin puede ver todos los offerings");
        }
        return repository.findAll();
    }
}
