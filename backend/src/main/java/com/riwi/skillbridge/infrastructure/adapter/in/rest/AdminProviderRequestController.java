package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ApproveProviderRequestUseCase;
import com.riwi.skillbridge.application.port.in.ListPendingProviderRequestsUseCase;
import com.riwi.skillbridge.application.port.in.RejectProviderRequestUseCase;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.DecideProviderRequestDto;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.PageResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ProviderRequestResponse;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/provider-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Solicitudes de Provider", description = "Revisión y decisión sobre solicitudes de Provider")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
    @ApiResponse(responseCode = "403", description = "El rol no es ADMIN", content = @Content)
})
public class AdminProviderRequestController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "status", "id");

    private final ListPendingProviderRequestsUseCase listPendingUseCase;
    private final ApproveProviderRequestUseCase approveUseCase;
    private final RejectProviderRequestUseCase rejectUseCase;
    private final UserAdminPort users;
    private final CurrentActorResolver actors;

    public AdminProviderRequestController(ListPendingProviderRequestsUseCase listPendingUseCase,
                                          ApproveProviderRequestUseCase approveUseCase,
                                          RejectProviderRequestUseCase rejectUseCase,
                                          UserAdminPort users,
                                          CurrentActorResolver actors) {
        this.listPendingUseCase = listPendingUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.users = users;
        this.actors = actors;
    }

    @GetMapping
    @Operation(summary = "Lista solicitudes pendientes de Provider paginadas")
    @ApiResponse(responseCode = "400", description = "Paginación u orden inválidos", content = @Content)
    public PageResponse<ProviderRequestResponse> listPending(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,asc") String sort,
        Authentication auth) {

        Actor actor = actors.from(auth);
        PageQuery query = toQuery(page, size, sort);
        PageResult<ProviderRequest> result = listPendingUseCase.listPending(actor, query);

        List<ProviderRequestResponse> items = result.items().stream()
            .map(r -> {
                UserAccount user = users.findById(r.userId()).orElse(null);
                return ProviderRequestResponse.from(r, user);
            })
            .toList();

        return new PageResponse<>(items, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Aprueba una solicitud de Provider y otorga el rol PROVIDER")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Solicitud aprobada"),
        @ApiResponse(responseCode = "404", description = "Solicitud o usuario no encontrado", content = @Content),
        @ApiResponse(responseCode = "422", description = "Regla de negocio: solicitud ya resuelta, propia o cuenta inactiva", content = @Content)
    })
    public ProviderRequestResponse approve(@PathVariable UUID id,
                                           @Valid @RequestBody(required = false) DecideProviderRequestDto dto,
                                           Authentication auth) {
        Actor actor = actors.from(auth);
        String reason = dto != null ? dto.reason() : null;
        ProviderRequest decided = approveUseCase.approve(actor, id, reason);
        UserAccount user = users.findById(decided.userId()).orElse(null);
        return ProviderRequestResponse.from(decided, user);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Rechaza una solicitud de Provider")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Solicitud rechazada"),
        @ApiResponse(responseCode = "404", description = "Solicitud no encontrada", content = @Content),
        @ApiResponse(responseCode = "422", description = "Regla de negocio: solicitud ya resuelta o propia", content = @Content)
    })
    public ProviderRequestResponse reject(@PathVariable UUID id,
                                          @Valid @RequestBody(required = false) DecideProviderRequestDto dto,
                                          Authentication auth) {
        Actor actor = actors.from(auth);
        String reason = dto != null ? dto.reason() : null;
        ProviderRequest decided = rejectUseCase.reject(actor, id, reason);
        UserAccount user = users.findById(decided.userId()).orElse(null);
        return ProviderRequestResponse.from(decided, user);
    }

    private static PageQuery toQuery(int page, int size, String sort) {
        if (page < 0 || size <= 0 || size > 100) {
            throw new IllegalArgumentException("Parámetros de paginación inválidos");
        }
        String[] parts = sort.split(",");
        String sortBy = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Campo de orden no permitido: " + sortBy);
        }
        String direction = parts.length > 1 ? parts[1].trim() : "asc";
        if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("La dirección de orden debe ser asc o desc");
        }
        return new PageQuery(page, size, sortBy, direction.equalsIgnoreCase("desc"));
    }
}
