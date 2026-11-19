package com.riwi.skillbridge.infrastructure.security;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentActorResolver {
    private static final String PREFIX = "ROLE_";
    private final UserAccountPort users;

    public CurrentActorResolver(UserAccountPort users) { this.users = users; }

    public Actor from(Authentication authentication) {
        String email = authentication.getName();
        UUID id = users.findIdByEmail(email)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
        Role role = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith(PREFIX))
            .map(a -> Role.valueOf(a.substring(PREFIX.length())))
            .findFirst()
            .orElseThrow(() -> new ForbiddenOperationException("Usuario sin rol"));
        return new Actor(id, email, role);
    }
}
