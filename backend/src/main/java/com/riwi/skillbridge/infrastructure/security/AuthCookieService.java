package com.riwi.skillbridge.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
public class AuthCookieService {
    private static final String COOKIE_PATH = "/api";

    private final String name;
    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public AuthCookieService(
        @Value("${app.jwt.cookie.name:access_token}") String name,
        @Value("${app.jwt.cookie.secure:true}") boolean secure,
        @Value("${app.jwt.cookie.same-site:Lax}") String sameSite,
        @Value("${app.jwt.expiration-minutes:120}") long expirationMinutes) {
        this.name = name;
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = Duration.ofMinutes(expirationMinutes);
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
            .filter(c -> name.equals(c.getName()))
            .map(Cookie::getValue)
            .filter(v -> !v.isBlank())
            .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(COOKIE_PATH);
    }
}
