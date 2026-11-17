package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ApproveProviderRequestUseCase;
import com.riwi.skillbridge.application.port.in.ListPendingProviderRequestsUseCase;
import com.riwi.skillbridge.application.port.in.RejectProviderRequestUseCase;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.ProviderRequest;
import com.riwi.skillbridge.domain.model.ProviderRequestStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.model.UserStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = AdminProviderRequestController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class, CurrentActorResolver.class})
class AdminProviderRequestControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Autowired MockMvc mvc;

    @MockitoBean ListPendingProviderRequestsUseCase listPendingUseCase;
    @MockitoBean ApproveProviderRequestUseCase approveUseCase;
    @MockitoBean RejectProviderRequestUseCase rejectUseCase;
    @MockitoBean UserAdminPort users;
    @MockitoBean UserAccountPort accounts;
    @MockitoBean JwtService jwtService;
    @MockitoBean DatabaseUserDetailsService userDetailsService;
    @MockitoBean AuthCookieService cookieService;

    private final UserAccount customerUser =
        new UserAccount(CUSTOMER_ID, "Carlos", "carlos@test.com", "hash", Role.CUSTOMER, UserStatus.ACTIVE);

    @BeforeEach
    void setUp() {
        when(accounts.findIdByEmail("admin@test.com")).thenReturn(Optional.of(ADMIN_ID));
        when(accounts.findIdByEmail("customer@test.com")).thenReturn(Optional.of(CUSTOMER_ID));
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customerUser));
    }

    // ---------- 401 Sin autenticar ----------

    @Test
    void sin_autenticacion_devuelve_401() throws Exception {
        mvc.perform(get("/api/admin/provider-requests")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/provider-requests/" + REQUEST_ID + "/approve").with(csrf()))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/provider-requests/" + REQUEST_ID + "/reject").with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    // ---------- 403 Para Customer y Provider ----------

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customer_no_puede_acceder_a_rutas_de_admin() throws Exception {
        mvc.perform(get("/api/admin/provider-requests")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/provider-requests/" + REQUEST_ID + "/approve").with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/provider-requests/" + REQUEST_ID + "/reject").with(csrf()))
            .andExpect(status().isForbidden());
        verify(approveUseCase, never()).approve(any(), any(), any());
    }

    // ---------- Admin: Listar solicitudes pendientes ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_lista_solicitudes_pendientes() throws Exception {
        ProviderRequest req = new ProviderRequest(
            REQUEST_ID, CUSTOMER_ID, ProviderRequestStatus.PENDING,
            "Quiero ofrecer servicios", Instant.now(), null, null, null);

        when(listPendingUseCase.listPending(eq(new Actor(ADMIN_ID, "admin@test.com", Role.ADMIN)), any()))
            .thenReturn(new PageResult<>(List.of(req), 0, 20, 1, 1));

        mvc.perform(get("/api/admin/provider-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(REQUEST_ID.toString()))
            .andExpect(jsonPath("$.items[0].userName").value("Carlos"))
            .andExpect(jsonPath("$.items[0].userEmail").value("carlos@test.com"))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------- Admin: Aprobar solicitud ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_aprueba_solicitud() throws Exception {
        Instant now = Instant.now();
        ProviderRequest approved = new ProviderRequest(
            REQUEST_ID, CUSTOMER_ID, ProviderRequestStatus.APPROVED,
            "Quiero ofrecer servicios", now.minusSeconds(100), ADMIN_ID, now, "Todo correcto");

        when(approveUseCase.approve(eq(new Actor(ADMIN_ID, "admin@test.com", Role.ADMIN)), eq(REQUEST_ID), eq("Todo correcto")))
            .thenReturn(approved);

        mvc.perform(put("/api/admin/provider-requests/" + REQUEST_ID + "/approve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Todo correcto\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(REQUEST_ID.toString()))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.decidedBy").value(ADMIN_ID.toString()))
            .andExpect(jsonPath("$.decisionReason").value("Todo correcto"));

        verify(approveUseCase).approve(any(), eq(REQUEST_ID), eq("Todo correcto"));
    }

    // ---------- Admin: Rechazar solicitud ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_rechaza_solicitud() throws Exception {
        Instant now = Instant.now();
        ProviderRequest rejected = new ProviderRequest(
            REQUEST_ID, CUSTOMER_ID, ProviderRequestStatus.REJECTED,
            "Quiero ofrecer servicios", now.minusSeconds(100), ADMIN_ID, now, "Falta documentación");

        when(rejectUseCase.reject(eq(new Actor(ADMIN_ID, "admin@test.com", Role.ADMIN)), eq(REQUEST_ID), eq("Falta documentación")))
            .thenReturn(rejected);

        mvc.perform(put("/api/admin/provider-requests/" + REQUEST_ID + "/reject")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Falta documentación\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(REQUEST_ID.toString()))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.decidedBy").value(ADMIN_ID.toString()))
            .andExpect(jsonPath("$.decisionReason").value("Falta documentación"));

        verify(rejectUseCase).reject(any(), eq(REQUEST_ID), eq("Falta documentación"));
    }

    // ---------- Reglas de negocio: 422 ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void aprobar_propia_solicitud_es_422() throws Exception {
        when(approveUseCase.approve(any(), eq(REQUEST_ID), any()))
            .thenThrow(new BusinessRuleException("No puedes resolver tu propia solicitud"));

        mvc.perform(put("/api/admin/provider-requests/" + REQUEST_ID + "/approve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Aprobado\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("No puedes resolver tu propia solicitud"));
    }
}
