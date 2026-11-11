package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.MentorshipResourceEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaMentorshipResourceRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/mentorship-resources")
public class MentorshipResourceController {
    private final JpaMentorshipResourceRepository resources;
    private final JpaOfferingRepository offerings;
    private final UserRepositoryPort users;
    public MentorshipResourceController(JpaMentorshipResourceRepository resources, JpaOfferingRepository offerings, UserRepositoryPort users) { this.resources = resources; this.offerings = offerings; this.users = users; }

    @GetMapping("/offerings/{offeringId}")
    public List<MentorshipResourceEntity> list(@PathVariable UUID offeringId, Authentication auth) { ownedOffering(offeringId, auth); return resources.findByOfferingIdOrderByCreatedAtAsc(offeringId); }

    @PostMapping("/offerings/{offeringId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MentorshipResourceEntity create(@PathVariable UUID offeringId, @Valid @RequestBody ResourceRequest request, Authentication auth) {
        ownedOffering(offeringId, auth);
        return resources.save(new MentorshipResourceEntity(UUID.randomUUID(), offeringId, request.title().trim(), request.resourceType(), request.url().trim()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        var resource = resources.findById(id).orElseThrow(() -> new DomainNotFoundException("Recurso no encontrado"));
        ownedOffering(resource.getOfferingId(), auth); resources.delete(resource);
    }

    private void ownedOffering(UUID offeringId, Authentication auth) {
        var offering = offerings.findById(offeringId).orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        var user = users.findByEmail(auth.getName()).orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        if (user.role() != Role.ADMIN && (user.role() != Role.PROVIDER || !offering.getProviderId().equals(user.id()))) throw new ForbiddenOperationException("No puedes administrar recursos de este servicio");
    }

    public record ResourceRequest(@NotBlank @Size(max = 160) String title, @Pattern(regexp = "VIDEO|DOCUMENT|LINK") String resourceType, @NotBlank @Size(max = 1500) String url) { }
}
