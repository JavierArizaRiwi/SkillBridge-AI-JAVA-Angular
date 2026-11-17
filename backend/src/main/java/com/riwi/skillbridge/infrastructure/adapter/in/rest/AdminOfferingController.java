package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ListAllOfferingsUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.PageResponse;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public PageResponse<OfferingResponse> all(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "title,asc") String sort,
                                              Authentication auth) {
        return PageResponse.from(listAll.listAll(actors.from(auth), PageParams.toQuery(page, size, sort)),
            OfferingResponse::from);
    }
}
