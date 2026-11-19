package com.riwi.skillbridge.infrastructure.security;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import org.springframework.security.core.userdetails.*;
import com.riwi.skillbridge.domain.model.UserStatus;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final JpaUserRepository users;

    public DatabaseUserDetailsService(JpaUserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return User.withUsername(user.getEmail())
            .password(user.getPassword())
            .roles(user.getRole().name())
            .disabled(user.getStatus() == UserStatus.SUSPENDED)
            .build();
    }
}
