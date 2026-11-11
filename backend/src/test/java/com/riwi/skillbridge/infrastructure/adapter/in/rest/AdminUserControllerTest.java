package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.out.UserAdminPort;
import com.riwi.skillbridge.application.service.UserAdminService;
import com.riwi.skillbridge.domain.model.PageQuery;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.policy.UserManagementPolicy;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.AuthCookieService;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadena real: seguridad (401/403) + controlador + servicio + política de usuarios.
 * Solo se simulan la base de datos y la tabla de usuarios.
 */
@WebMvcTest(
    controllers = AdminUserController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class, UserAdminService.class,
    UserManagementPolicy.class, CurrentActorResolver.class})
class AdminUserControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String STATUS_BODY = "{\"status\":\"SUSPENDED\"}";
    private static final String ROLE_BODY = "{\"role\":\"PROVIDER\"}";

    @Autowired MockMvc mvc;

    @MockitoBean UserAdminPort users;
    @MockitoBean UserAccountPort accounts;
    // Dependencias del filtro JWT (aquí la autenticación la simula @WithMockUser)
    @MockitoBean JwtService jwtService;
    @MockitoBean DatabaseUserDetailsService userDetailsService;
    @MockitoBean AuthCookieService cookieService;

    private final UserAccount customer =
        new UserAccount(UUID.randomUUID(), "Ana", "ana@test.com", "secret-hash", Role.CUSTOMER);
    private final UserAccount admin =
        new UserAccount(ADMIN_ID, "Root", "admin@test.com", "secret-hash", Role.ADMIN);

    @BeforeEach
    void setUp() {
        when(accounts.findIdByEmail("admin@test.com")).thenReturn(Optional.of(ADMIN_ID));
        when(accounts.findIdByEmail("customer@test.com")).thenReturn(Optional.of(UUID.randomUUID()));
        when(accounts.findIdByEmail("provider@test.com")).thenReturn(Optional.of(UUID.randomUUID()));
        when(users.findById(customer.id())).thenReturn(Optional.of(customer));
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.findAll(any())).thenReturn(new PageResult<>(List.of(customer), 0, 20, 1, 1));
    }

    private String statusUrl(UUID id) { return "/api/admin/users/" + id + "/status"; }
    private String roleUrl(UUID id) { return "/api/admin/users/" + id + "/role"; }

    // ---------- Sin autenticar: 401 ----------

    @Test
    void sin_token_todas_las_operaciones_son_401() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/" + customer.id())).andExpect(status().isUnauthorized());
        mvc.perform(put(statusUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
            .andExpect(status().isUnauthorized());
        mvc.perform(put(roleUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY))
            .andExpect(status().isUnauthorized());
        verify(users, never()).save(any());
    }

    // ---------- Customer y Provider: 403 ----------

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customer_no_puede_usar_operaciones_administrativas() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(put(statusUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
            .andExpect(status().isForbidden());
        mvc.perform(put(roleUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY))
            .andExpect(status().isForbidden());
        verify(users, never()).save(any());
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_no_puede_usar_operaciones_administrativas() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mvc.perform(put(roleUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY))
            .andExpect(status().isForbidden());
        verify(users, never()).save(any());
    }

    // ---------- Admin: listar ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_lista_con_valores_por_defecto_y_sin_exponer_el_hash() throws Exception {
        mvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].email").value("ana@test.com"))
            .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(content().string(not(containsString("secret-hash"))));

        verify(users).findAll(new PageQuery(0, 20, "createdAt", true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_pagina_y_ordena_segun_los_parametros() throws Exception {
        mvc.perform(get("/api/admin/users?page=2&size=5&sort=email,asc")).andExpect(status().isOk());

        verify(users).findAll(new PageQuery(2, 5, "email", false));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void orden_por_campo_no_permitido_es_400() throws Exception {
        mvc.perform(get("/api/admin/users?sort=passwordHash,asc")).andExpect(status().isBadRequest());
        verify(users, never()).findAll(any());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void direccion_de_orden_invalida_es_400() throws Exception {
        mvc.perform(get("/api/admin/users?sort=email,sideways")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void tamano_de_pagina_fuera_de_rango_es_400() throws Exception {
        mvc.perform(get("/api/admin/users?size=1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users?size=0")).andExpect(status().isBadRequest());
    }

    // ---------- Admin: detalle ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_ve_el_detalle_sin_exponer_el_hash() throws Exception {
        mvc.perform(get("/api/admin/users/" + customer.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(content().string(not(containsString("secret-hash"))));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void detalle_de_usuario_inexistente_es_404() throws Exception {
        mvc.perform(get("/api/admin/users/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ---------- Admin: cambiar estado ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_suspende_a_otro_usuario() throws Exception {
        mvc.perform(put(statusUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(users).save(argThat(u -> u.id().equals(customer.id()) && u.status().name().equals("SUSPENDED")));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_no_puede_cambiar_su_propio_estado() throws Exception {
        mvc.perform(put(statusUrl(ADMIN_ID)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
            .andExpect(status().isUnprocessableEntity());
        verify(users, never()).save(any());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void poner_el_mismo_estado_es_422() throws Exception {
        mvc.perform(put(statusUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void estado_invalido_o_ausente_es_400() throws Exception {
        mvc.perform(put(statusUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BANNED\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put(statusUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verify(users, never()).save(any());
    }

    // ---------- Admin: cambiar rol ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_cambia_el_rol_de_otro_usuario() throws Exception {
        mvc.perform(put(roleUrl(customer.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ROLE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("PROVIDER"));

        verify(users).save(argThat(u -> u.id().equals(customer.id()) && u.role() == Role.PROVIDER));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_no_puede_cambiar_su_propio_rol() throws Exception {
        mvc.perform(put(roleUrl(ADMIN_ID)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"CUSTOMER\"}"))
            .andExpect(status().isUnprocessableEntity());
        verify(users, never()).save(any());
    }
}


