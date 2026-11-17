package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreateProviderRequestUseCase;
import com.riwi.skillbridge.application.port.in.GetMyProviderRequestsUseCase;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateProviderRequestDto;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ProviderRequestResponse;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/provider-requests")
@Tag(name = "Customer - Solicitudes de Provider", description = "Solicitudes para convertirse en Provider")
public class CustomerProviderRequestController {

    private final CreateProviderRequestUseCase createUseCase;
    private final GetMyProviderRequestsUseCase getMyRequestsUseCase;
    private final CurrentActorResolver actors;

    public CustomerProviderRequestController(CreateProviderRequestUseCase createUseCase,
                                           GetMyProviderRequestsUseCase getMyRequestsUseCase,
                                           CurrentActorResolver actors) {
        this.createUseCase = createUseCase;
        this.getMyRequestsUseCase = getMyRequestsUseCase;
        this.actors = actors;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Un Customer solicita convertirse en Provider")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Solicitud creada"),
        @ApiResponse(responseCode = "400", description = "Motivo inválido o ausente", content = @Content),
        @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
        @ApiResponse(responseCode = "403", description = "El rol no es CUSTOMER", content = @Content),
        @ApiResponse(responseCode = "422", description = "Ya existe solicitud pendiente o usuario ya es Provider", content = @Content)
    })
    public ProviderRequestResponse create(@Valid @RequestBody CreateProviderRequestDto dto,
                                          Authentication auth) {
        Actor actor = actors.from(auth);
        return ProviderRequestResponse.from(createUseCase.create(actor, dto.message()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulta las solicitudes de Provider del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial de solicitudes"),
        @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content)
    })
    public List<ProviderRequestResponse> myRequests(Authentication auth) {
        Actor actor = actors.from(auth);
        return getMyRequestsUseCase.getMyRequests(actor).stream()
            .map(ProviderRequestResponse::from)
            .toList();
    }
}
