package com.riwi.skillbridge.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final DatabaseUserDetailsService userDetailsService;
    private final AuthCookieService cookieService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   DatabaseUserDetailsService userDetailsService,
                                   AuthCookieService cookieService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.cookieService = cookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails user = userDetailsService.loadUserByUsername(username);
                if (user.isEnabled() && jwtService.isValid(token, user)) {
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    // Cookie primero; header Bearer como respaldo (Postman)
    private String resolveToken(HttpServletRequest request) {
        return cookieService.resolve(request).orElseGet(() -> {
            String header = request.getHeader("Authorization");
            return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        });
    }
}
