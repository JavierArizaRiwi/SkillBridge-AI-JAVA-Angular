package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ListAllOfferingsUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingResponse;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/offerings")
public class AdminOfferingController {
    private final ListAllOfferingsUseCase listAll;
    private final CurrentActorResolver actors;

    public AdminOfferingController(ListAllOfferingsUseCase listAll, CurrentActorResolver actors) {
        this.listAll = listAll;
        this.actors = actors;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<OfferingResponse> all(Authentication auth) {
        return listAll.listAll(actors.from(auth)).stream().map(OfferingResponse::from).toList();
    }
}
