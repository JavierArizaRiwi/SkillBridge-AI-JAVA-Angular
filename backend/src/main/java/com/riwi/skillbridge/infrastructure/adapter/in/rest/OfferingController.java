package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.*;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateOfferingRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UpdateOfferingRequest;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/offerings")
@Tag(name = "Offerings", description = "Catálogo de servicios y gestión por Provider y Admin")
public class OfferingController {
    private final ListOfferingsUseCase listActive;
    private final ListMyOfferingsUseCase listMine;
    private final CreateOfferingUseCase create;
    private final UpdateOfferingUseCase update;
    private final DeactivateOfferingUseCase deactivate;
    private final ActivateOfferingUseCase activate;
    private final CurrentActorResolver actors;

    public OfferingController(ListOfferingsUseCase listActive, ListMyOfferingsUseCase listMine,
                              CreateOfferingUseCase create, UpdateOfferingUseCase update,
                              DeactivateOfferingUseCase deactivate, ActivateOfferingUseCase activate,
                              CurrentActorResolver actors) {
        this.listActive = listActive;
        this.listMine = listMine;
        this.create = create;
        this.update = update;
        this.deactivate = deactivate;
        this.activate = activate;
        this.actors = actors;
    }

    @GetMapping
    @Operation(summary = "Lista el catálogo de offerings activos (público)")
    public List<OfferingResponse> list() {
        return listActive.listActive().stream().map(OfferingResponse::from).toList();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PROVIDER')")
    @Operation(summary = "Lista los offerings del Provider autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
        @ApiResponse(responseCode = "403", description = "El rol no es PROVIDER", content = @Content)
    })
    public List<OfferingResponse> mine(Authentication auth) {
        return listMine.listMine(actors.from(auth)).stream().map(OfferingResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Crea un offering (el dueño es el Provider autenticado; el Admin indica providerId)")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
        @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
        @ApiResponse(responseCode = "403", description = "El rol no es PROVIDER ni ADMIN", content = @Content)
    })
    public OfferingResponse create(@Valid @RequestBody CreateOfferingRequest req, Authentication auth) {
        var command = new CreateOfferingUseCase.Command(req.providerId(), req.title(),
            req.description(), req.category(), req.price());
        return OfferingResponse.from(create.create(actors.from(auth), command));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Actualiza un offering propio (el Admin puede actualizar cualquiera)")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
        @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
        @ApiResponse(responseCode = "403", description = "Rol sin permiso u offering ajeno", content = @Content),
        @ApiResponse(responseCode = "404", description = "Offering no encontrado", content = @Content)
    })
    public OfferingResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOfferingRequest req,
                                   Authentication auth) {
        var command = new UpdateOfferingUseCase.Command(req.title(), req.description(),
            req.category(), req.price());
        return OfferingResponse.from(update.update(actors.from(auth), id, command));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Desactiva un offering propio (el Admin puede desactivar cualquiera)")
    @ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
        @ApiResponse(responseCode = "403", description = "Rol sin permiso u offering ajeno", content = @Content),
        @ApiResponse(responseCode = "404", description = "Offering no encontrado", content = @Content)
    })
    public void deactivate(@PathVariable UUID id, Authentication auth) {
        deactivate.deactivate(actors.from(auth), id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Reactiva un offering propio (el Admin puede reactivar cualquiera)")
    @ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
        @ApiResponse(responseCode = "403", description = "Rol sin permiso u offering ajeno", content = @Content),
        @ApiResponse(responseCode = "404", description = "Offering no encontrado", content = @Content)
    })
    public void activate(@PathVariable UUID id, Authentication auth) {
        activate.activate(actors.from(auth), id);
    }
}
