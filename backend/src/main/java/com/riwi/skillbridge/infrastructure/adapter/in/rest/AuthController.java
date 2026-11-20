package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AuthResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.LoginRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.RegisterRequest;
import com.riwi.skillbridge.infrastructure.security.AuthCookieService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthUseCase auth;
    private final JwtService jwtService;
    private final AuthCookieService cookies;

    public AuthController(AuthUseCase auth, JwtService jwtService, AuthCookieService cookies) {
        this.auth = auth;
        this.jwtService = jwtService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        String token = auth.register(request.name(), request.email(), request.password());
        return session(HttpStatus.CREATED, token);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = auth.login(request.email(), request.password());
        return session(HttpStatus.OK, token);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
            .build();
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal UserDetails user) {
        String role = user.getAuthorities().stream()
            .findFirst()
            .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
            .orElse(null);
        return new AuthResponse(user.getUsername(), role);
    }

    private ResponseEntity<AuthResponse> session(HttpStatus status, String token) {
        AuthResponse body = new AuthResponse(jwtService.extractUsername(token), jwtService.extractRole(token));
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, cookies.create(token).toString())
            .body(body);
    }
}
