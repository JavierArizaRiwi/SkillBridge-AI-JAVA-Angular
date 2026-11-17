package com.riwi.skillbridge.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        }
        if (csrfToken != null) {
            String token = csrfToken.getToken(); // fuerza la escritura de la cookie XSRF-TOKEN
            // Exponemos el token en un header para clientes cross-domain
            response.setHeader("X-XSRF-TOKEN", token);
        }
        chain.doFilter(request, response);
    }
}