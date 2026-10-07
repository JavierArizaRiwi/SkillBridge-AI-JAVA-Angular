package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.OfferingCommandService;
import com.riwi.skillbridge.application.service.OfferingQueryService;
import com.riwi.skillbridge.application.service.OfferingService;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.policy.OfferingAccessPolicy;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la cadena real: seguridad (401/403) + controlador + servicios + política de ownership.
 * Solo se simulan la base de datos, la caché y la tabla de usuarios.
 */
@WebMvcTest(
    controllers = {OfferingController.class, AdminOfferingController.class},
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class, OfferingCommandService.class,
    OfferingQueryService.class, OfferingService.class, OfferingAccessPolicy.class,
    CurrentActorResolver.class})
class OfferingSecurityTest {

    private static final UUID PROVIDER_ID = UUID.randomUUID();
    private static final UUID OTHER_PROVIDER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private static final String BODY =
        "{\"title\":\"Java\",\"description\":\"desc\",\"category\":\"BACKEND\",\"price\":100}";

    @Autowired MockMvc mvc;

    @MockitoBean OfferingRepositoryPort repository;
    @MockitoBean OfferingCachePort cache;
    @MockitoBean UserAccountPort users;
    // Dependencias del filtro JWT (aquí la autenticación la simula @WithMockUser)
    @MockitoBean JwtService jwtService;
    @MockitoBean DatabaseUserDetailsService userDetailsService;

    private final Offering own = new Offering(UUID.randomUUID(), PROVIDER_ID,
        "Propio", "desc", "BACKEND", BigDecimal.TEN, true);
    private final Offering foreign = new Offering(UUID.randomUUID(), OTHER_PROVIDER_ID,
        "Ajeno", "desc", "BACKEND", BigDecimal.TEN, true);

    @BeforeEach
    void setUp() {
        when(users.findIdByEmail("provider@test.com")).thenReturn(Optional.of(PROVIDER_ID));
        when(users.findIdByEmail("admin@test.com")).thenReturn(Optional.of(ADMIN_ID));
        when(users.findIdByEmail("customer@test.com")).thenReturn(Optional.of(CUSTOMER_ID));
        when(repository.findById(own.id())).thenReturn(Optional.of(own));
        when(repository.findById(foreign.id())).thenReturn(Optional.of(foreign));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- Sin autenticar: 401 ----------

    @Test
    void catalogo_publico_no_requiere_token() throws Exception {
        mvc.perform(get("/api/offerings")).andExpect(status().isOk());
    }

    @Test
    void sin_token_no_puede_listar_propios() throws Exception {
        mvc.perform(get("/api/offerings/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void sin_token_no_puede_crear() throws Exception {
        mvc.perform(post("/api/offerings").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        verify(repository, never()).save(any());
    }

    // ---------- Customer: 403 ----------

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customer_no_puede_crear() throws Exception {
        mvc.perform(post("/api/offerings").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verify(repository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void customer_no_puede_listar_propios() throws Exception {
        mvc.perform(get("/api/offerings/me")).andExpect(status().isForbidden());
    }

    // ---------- Provider: propios OK, ajenos 403 ----------

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_crea_y_el_dueno_es_el_autenticado_aunque_envie_otro_providerId() throws Exception {
        String manipulado = "{\"providerId\":\"" + OTHER_PROVIDER_ID
            + "\",\"title\":\"Java\",\"description\":\"desc\",\"category\":\"BACKEND\",\"price\":100}";

        mvc.perform(post("/api/offerings").contentType(MediaType.APPLICATION_JSON).content(manipulado))
            .andExpect(status().isCreated());

        verify(repository).save(argThat(o -> o.providerId().equals(PROVIDER_ID)));
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_lista_solo_los_suyos() throws Exception {
        mvc.perform(get("/api/offerings/me")).andExpect(status().isOk());

        verify(repository).findByProviderId(PROVIDER_ID);
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_actualiza_su_offering() throws Exception {
        mvc.perform(put("/api/offerings/" + own.id()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_no_puede_actualizar_offering_ajeno() throws Exception {
        mvc.perform(put("/api/offerings/" + foreign.id()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verify(repository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_desactiva_su_offering() throws Exception {
        mvc.perform(post("/api/offerings/" + own.id() + "/deactivate"))
            .andExpect(status().isNoContent());

        verify(repository).save(argThat(o -> !o.active()));
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_no_puede_desactivar_offering_ajeno() throws Exception {
        mvc.perform(post("/api/offerings/" + foreign.id() + "/deactivate"))
            .andExpect(status().isForbidden());
        verify(repository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void id_inexistente_responde_404() throws Exception {
        mvc.perform(put("/api/offerings/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    // ---------- Admin: todo OK ----------

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_desactiva_offering_de_cualquier_provider() throws Exception {
        mvc.perform(post("/api/offerings/" + foreign.id() + "/deactivate"))
            .andExpect(status().isNoContent());

        verify(repository).save(argThat(o -> !o.active() && o.providerId().equals(OTHER_PROVIDER_ID)));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_crea_offering_para_un_provider() throws Exception {
        String conDueno = "{\"providerId\":\"" + OTHER_PROVIDER_ID
            + "\",\"title\":\"Java\",\"description\":\"desc\",\"category\":\"BACKEND\",\"price\":100}";

        mvc.perform(post("/api/offerings").contentType(MediaType.APPLICATION_JSON).content(conDueno))
            .andExpect(status().isCreated());

        verify(repository).save(argThat(o -> o.providerId().equals(OTHER_PROVIDER_ID)));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_lista_todos_los_offerings() throws Exception {
        mvc.perform(get("/api/admin/offerings")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "provider@test.com", roles = "PROVIDER")
    void provider_no_puede_usar_el_endpoint_de_admin() throws Exception {
        mvc.perform(get("/api/admin/offerings")).andExpect(status().isForbidden());
    }
}
