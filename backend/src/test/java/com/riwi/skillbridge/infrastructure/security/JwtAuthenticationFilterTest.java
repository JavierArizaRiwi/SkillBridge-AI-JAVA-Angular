package com.riwi.skillbridge.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TOKEN = "token-de-prueba";

    private final JwtService jwtService = mock(JwtService.class);
    private final DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);
    private final AuthCookieService cookieService = mock(AuthCookieService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService, cookieService);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void ejecutarConToken(UserDetails user) throws Exception {
        when(jwtService.extractUsername(TOKEN)).thenReturn("ana@test.com");
        when(userDetailsService.loadUserByUsername("ana@test.com")).thenReturn(user);
        when(jwtService.isValid(TOKEN, user)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void token_valido_de_cuenta_activa_autentica() throws Exception {
        ejecutarConToken(User.withUsername("ana@test.com").password("x").roles("CUSTOMER").build());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void token_valido_de_cuenta_suspendida_no_autentica() throws Exception {
        ejecutarConToken(User.withUsername("ana@test.com").password("x").roles("CUSTOMER").disabled(true).build());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

