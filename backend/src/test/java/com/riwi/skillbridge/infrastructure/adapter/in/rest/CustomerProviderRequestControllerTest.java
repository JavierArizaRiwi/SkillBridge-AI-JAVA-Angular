package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreateProviderRequestUseCase;
import com.riwi.skillbridge.application.port.in.GetMyProviderRequestsUseCase;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.AuthCookieService;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = CustomerProviderRequestController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class, CurrentActorResolver.class})
class CustomerProviderRequestControllerTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String REQUEST_BODY = "{\"message\":\"Deseo dar clases de programación\"}";

    @Autowired MockMvc mvc;

    @MockitoBean CreateProviderRequestUseCase createUseCase;
    @MockitoBean GetMyProviderRequestsUseCase getMyRequestsUseCase;
    @MockitoBean UserAccountPort accounts;
    @MockitoBean JwtService jwtService;
    @MockitoBean DatabaseUserDetailsService userDetailsService;
    @MockitoBean AuthCookieService cookieService;

    @BeforeEach
    void setUp() {
        when(accounts.findIdByEmail("customer@test.com")).thenReturn(Optional.of(CUSTOMER_ID));
        when(accounts.findIdByEmail("admin@test.com")).thenReturn(Optional.of(UUID.randomUUID()));
        when(accounts.findIdByEmail("provider@test.com")).thenReturn(Optional.of(UUID.randomUUID()));
    }

    // ---------- 401 Sin autenticar ----------

    @Test
    void sin_autenticacion_devuelve_401() throws Exception {
        mvc.perform(post("/api/provider-requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/provider-requests/me"))
            .andExpect(status().isUnauthorized());

        verify(createUseCase, never()).create(any(), any());
    }

    // ---------- 403 Rol no autorizado ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_no_puede_crear_solicitud_de_provider() throws Exception {
        mvc.perform(post("/api/provider-requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY))
            .andExpect(status().isForbidden());

        verify(createUseCase, never()).create(any(), any());
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_no_puede_crear_solicitud_de_provider() throws Exception {
        mvc.perform(post("/api/provider-requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY))
            .andExpect(status().isForbidden());

        verify(createUseCase, never()).create(any(), any());
    }

    // ---------- Operaciones válidas del Customer ----------

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customer_crea_solicitud_exitosamente() throws Exception {
        ProviderRequest created = new ProviderRequest(
            UUID.randomUUID(), CUSTOMER_ID, ProviderRequestStatus.PENDING,
            "Deseo dar clases de programación", Instant.now(), null, null, null);

        when(createUseCase.create(eq(new Actor(CUSTOMER_ID, "customer@test.com", Role.CUSTOMER)), eq("Deseo dar clases de programación")))
            .thenReturn(created);

        mvc.perform(post("/api/provider-requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(created.id().toString()))
            .andExpect(jsonPath("$.userId").value(CUSTOMER_ID.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.message").value("Deseo dar clases de programación"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void solicitud_con_motivo_en_blanco_devuelve_400() throws Exception {
        mvc.perform(post("/api/provider-requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \"}"))
            .andExpect(status().isBadRequest());

        verify(createUseCase, never()).create(any(), any());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void si_ya_existe_solicitud_pendiente_devuelve_422() throws Exception {
        when(createUseCase.create(any(), any()))
            .thenThrow(new BusinessRuleException("Ya existe una solicitud pendiente para este usuario"));

        mvc.perform(post("/api/provider-requests")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Ya existe una solicitud pendiente para este usuario"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customer_consulta_sus_propias_solicitudes() throws Exception {
        ProviderRequest req = new ProviderRequest(
            UUID.randomUUID(), CUSTOMER_ID, ProviderRequestStatus.PENDING,
            "Deseo ser tutor", Instant.now(), null, null, null);

        when(getMyRequestsUseCase.getMyRequests(eq(new Actor(CUSTOMER_ID, "customer@test.com", Role.CUSTOMER))))
            .thenReturn(List.of(req));

        mvc.perform(get("/api/provider-requests/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(req.id().toString()))
            .andExpect(jsonPath("$[0].userId").value(CUSTOMER_ID.toString()))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }
}
