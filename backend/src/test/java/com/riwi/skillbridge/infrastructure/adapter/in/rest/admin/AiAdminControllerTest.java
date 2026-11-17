package com.riwi.skillbridge.infrastructure.adapter.in.rest.admin;

import com.riwi.skillbridge.infrastructure.adapter.out.ai.classification.AiErrorClassifier;
import com.riwi.skillbridge.infrastructure.adapter.out.ai.resilience.AiCircuitBreakerManager;
import com.riwi.skillbridge.infrastructure.config.AiFallbackProperties;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.AuthCookieService;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AiAdminController.class,
        properties = "app.cors.allowed-origins=http://localhost:4200"
)
@Import({
        SecurityConfiguration.class,
        RestAccessDeniedHandler.class,
        AiFallbackProperties.class,
        AiErrorClassifier.class,
        AiCircuitBreakerManager.class
})
class AiAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @Test
    @DisplayName("Scenario 3.2: 401 Unauthorized when unauthenticated / anonymous user calls GET /api/admin/ai/providers")
    void shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@skillbridge.ai", roles = {"CUSTOMER"})
    @DisplayName("Scenario 3.2: 403 Forbidden when authenticated user lacks ROLE_ADMIN (e.g. ROLE_CUSTOMER)")
    void shouldReturn403WhenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value(containsString("No tienes permisos")));
    }

    @Test
    @WithMockUser(username = "provider@skillbridge.ai", roles = {"PROVIDER"})
    @DisplayName("Scenario 3.2: 403 Forbidden when user has ROLE_PROVIDER")
    void shouldReturn403WhenProviderRole() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@skillbridge.ai", roles = {"ADMIN"})
    @DisplayName("Scenario 3.1 & 3.4: 200 OK for ROLE_ADMIN with effective chain and circuits; zero secrets exposed")
    void shouldReturn200AndSanitizedStatusForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.capabilities.chat.capability").value("chat"))
                .andExpect(jsonPath("$.capabilities.chat.chain[0].provider").value("gemini"))
                .andExpect(jsonPath("$.capabilities.chat.chain[0].circuitState").value("CLOSED"))
                .andExpect(jsonPath("$.capabilities.vision.capability").value("vision"))
                .andExpect(jsonPath("$.capabilities.stt.capability").value("stt"))
                .andExpect(content().string(not(containsString("key"))))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("token"))));
    }
}
