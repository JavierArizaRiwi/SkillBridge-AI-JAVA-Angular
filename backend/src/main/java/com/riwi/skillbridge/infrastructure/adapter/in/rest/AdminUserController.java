package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ChangeUserRoleUseCase;
import com.riwi.skillbridge.application.port.in.ChangeUserStatusUseCase;
import com.riwi.skillbridge.application.port.in.GetUserUseCase;
import com.riwi.skillbridge.application.port.in.ListUsersUseCase;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ChangeUserRoleRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ChangeUserStatusRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.PageResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UserResponse;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Usuarios", description = "Gestión de usuarios de la plataforma (solo ADMIN)")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Sin autenticar", content = @Content),
    @ApiResponse(responseCode = "403", description = "El rol no es ADMIN", content = @Content)
})
public class AdminUserController {
    private final ListUsersUseCase listUsers;
    private final GetUserUseCase getUser;
    private final ChangeUserStatusUseCase changeStatus;
    private final ChangeUserRoleUseCase changeRole;
    private final CurrentActorResolver actors;

    public AdminUserController(ListUsersUseCase listUsers, GetUserUseCase getUser,
                               ChangeUserStatusUseCase changeStatus, ChangeUserRoleUseCase changeRole,
                               CurrentActorResolver actors) {
        this.listUsers = listUsers;
        this.getUser = getUser;
        this.changeStatus = changeStatus;
        this.changeRole = changeRole;
        this.actors = actors;
    }

    @GetMapping
    @Operation(summary = "Lista usuarios paginados. sort=campo,asc|desc (name, email, role, status, createdAt)")
    @ApiResponse(responseCode = "400", description = "Paginación u orden inválidos", content = @Content)
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "createdAt,desc") String sort,
                                           Authentication auth) {
        return PageResponse.from(listUsers.listUsers(actors.from(auth), toQuery(page, size, sort)),
            UserResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un usuario")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content)
    public UserResponse detail(@PathVariable UUID id, Authentication auth) {
        return UserResponse.from(getUser.getUser(actors.from(auth), id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Cambia el estado de la cuenta (ACTIVE o SUSPENDED)")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Estado inválido", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content),
        @ApiResponse(responseCode = "422", description = "Regla de negocio: propia cuenta o mismo estado", content = @Content)
    })
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeUserStatusRequest req,
                                     Authentication auth) {
        return UserResponse.from(changeStatus.changeStatus(actors.from(auth), id, req.status()));
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Cambia el rol del usuario (CUSTOMER, PROVIDER o ADMIN)")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "Rol inválido", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content),
        @ApiResponse(responseCode = "422", description = "Regla de negocio: propia cuenta o mismo rol", content = @Content)
    })
    public UserResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeUserRoleRequest req,
                                   Authentication auth) {
        return UserResponse.from(changeRole.changeRole(actors.from(auth), id, req.role()));
    }

    private static PageQuery toQuery(int page, int size, String sort) {
        String[] parts = sort.split(",");
        String direction = parts.length > 1 ? parts[1].trim() : "asc";
        if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("La dirección de orden debe ser asc o desc");
        }
        return new PageQuery(page, size, parts[0].trim(), direction.equalsIgnoreCase("desc"));
    }
}
