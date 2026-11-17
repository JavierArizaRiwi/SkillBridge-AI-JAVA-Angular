package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AskKnowledgeUseCase;
import com.riwi.skillbridge.domain.exception.AiProviderException;
import com.riwi.skillbridge.domain.knowledge.KnowledgeAnswer;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import com.riwi.skillbridge.domain.knowledge.KnowledgeSource;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = KnowledgeController.class,
        properties = {"app.cors.allowed-origins=http://localhost:4200", "app.rag.min-score-threshold=0.5"}
)
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class KnowledgeControllerTest {

    private static final String ASK_ENDPOINT = "/api/ai/knowledge/ask";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AskKnowledgeUseCase askKnowledgeUseCase;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @Test
    @DisplayName("200 OK con la forma exacta del contrato (FR-003)")
    @WithMockUser(username = "user@test.com")
    void ask_conPreguntaValida_retorna200ConFormaDelContrato() throws Exception {
        KnowledgeSource source = new KnowledgeSource(
                "cancellation-policy",
                "Política de Cancelación",
                "Las reservas pueden cancelarse con 24 horas de antelación.",
                0.82
        );
        KnowledgeAnswer answer = new KnowledgeAnswer(
                "Puedes cancelar la reserva con al menos 24 horas de antelación sin penalización.",
                true,
                List.of(source)
        );

        when(askKnowledgeUseCase.ask(any(KnowledgeQuery.class))).thenReturn(answer);

        String payload = """
                {
                    "question": "¿Con cuánta anticipación puedo cancelar una reserva?"
                }
                """;

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Puedes cancelar la reserva con al menos 24 horas de antelación sin penalización."))
                .andExpect(jsonPath("$.hasSufficientContext").value(true))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].sourceId").value("cancellation-policy"))
                .andExpect(jsonPath("$.sources[0].title").value("Política de Cancelación"))
                .andExpect(jsonPath("$.sources[0].snippet").value("Las reservas pueden cancelarse con 24 horas de antelación."))
                .andExpect(jsonPath("$.sources[0].score").value(0.82));

        verify(askKnowledgeUseCase).ask(any(KnowledgeQuery.class));
    }

    @Test
    @DisplayName("200 OK caso sin información suficiente (FR-006, sources vacío)")
    @WithMockUser(username = "user@test.com")
    void ask_cuandoNoHayInformacionSuficiente_retorna200ConFuentesVacias() throws Exception {
        KnowledgeAnswer answer = new KnowledgeAnswer(
                "No dispongo de información suficiente sobre esa consulta en la plataforma.",
                false,
                List.of()
        );

        when(askKnowledgeUseCase.ask(any(KnowledgeQuery.class))).thenReturn(answer);

        String payload = """
                {
                    "question": "¿Cuál es la receta de una paella valenciana?"
                }
                """;

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("No dispongo de información suficiente sobre esa consulta en la plataforma."))
                .andExpect(jsonPath("$.hasSufficientContext").value(false))
                .andExpect(jsonPath("$.sources", hasSize(0)));

        verify(askKnowledgeUseCase).ask(any(KnowledgeQuery.class));
    }

    @Test
    @DisplayName("400 Bad Request cuando la pregunta contiene solo espacios en blanco (FR-002)")
    @WithMockUser(username = "user@test.com")
    void ask_conPreguntaSoloEspacios_retorna400ProblemDetail() throws Exception {
        String payload = """
                {
                    "question": "   "
                }
                """;

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.status").value(400));

        verify(askKnowledgeUseCase, never()).ask(any(KnowledgeQuery.class));
    }

    @Test
    @DisplayName("400 Bad Request cuando la pregunta supera los 500 caracteres (501 caracteres) (FR-002)")
    @WithMockUser(username = "user@test.com")
    void ask_conPreguntaDe501Caracteres_retorna400ProblemDetail() throws Exception {
        String longQuestion = "a".repeat(501);
        String payload = "{\"question\":\"" + longQuestion + "\"}";

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.status").value(400));

        verify(askKnowledgeUseCase, never()).ask(any(KnowledgeQuery.class));
    }

    @Test
    @DisplayName("200 OK con pregunta de exactamente 500 caracteres (límite superior permitido)")
    @WithMockUser(username = "user@test.com")
    void ask_conPreguntaDe500Caracteres_esValidaYRetorna200() throws Exception {
        String exact500Question = "a".repeat(500);
        String payload = "{\"question\":\"" + exact500Question + "\"}";

        when(askKnowledgeUseCase.ask(any(KnowledgeQuery.class))).thenReturn(
                new KnowledgeAnswer("Respuesta límite", true, List.of())
        );

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Respuesta límite"))
                .andExpect(jsonPath("$.hasSufficientContext").value(true));

        verify(askKnowledgeUseCase).ask(any(KnowledgeQuery.class));
    }

    @Test
    @DisplayName("401 Unauthorized sin autenticación (FR-001, SC-001)")
    void ask_sinAutenticacion_retorna401() throws Exception {
        String payload = """
                {
                    "question": "¿Con cuánta anticipación puedo cancelar una reserva?"
                }
                """;

        mvc.perform(post(ASK_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verify(askKnowledgeUseCase, never()).ask(any(KnowledgeQuery.class));
    }

    @Test
    @DisplayName("503 Service Unavailable cuando el proveedor de IA falla con TIMEOUT (FR-008, contrato)")
    @WithMockUser(username = "user@test.com")
    void ask_cuandoProveedorIaFallaConTimeout_retorna503ProblemDetail() throws Exception {
        when(askKnowledgeUseCase.ask(any(KnowledgeQuery.class))).thenThrow(
                new AiProviderException("Tiempo de espera agotado", AiProviderException.ErrorType.TIMEOUT)
        );

        String payload = """
                {
                    "question": "¿Con cuánta anticipación puedo cancelar una reserva?"
                }
                """;

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("Timeout"));
    }

    @Test
    @DisplayName("503 Service Unavailable cuando el proveedor de IA está UNAVAILABLE (FR-009, contrato)")
    @WithMockUser(username = "user@test.com")
    void ask_cuandoProveedorIaFallaConUnavailable_retorna503ProblemDetail() throws Exception {
        when(askKnowledgeUseCase.ask(any(KnowledgeQuery.class))).thenThrow(
                new AiProviderException("Servicio no disponible", AiProviderException.ErrorType.UNAVAILABLE)
        );

        String payload = """
                {
                    "question": "¿Con cuánta anticipación puedo cancelar una reserva?"
                }
                """;

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("Unavailable"));
    }

    @Test
    @DisplayName("429 Too Many Requests cuando el proveedor de IA excede cuota RATE_LIMITED (contrato)")
    @WithMockUser(username = "user@test.com")
    void ask_cuandoProveedorIaFallaConRateLimit_retorna429ProblemDetail() throws Exception {
        when(askKnowledgeUseCase.ask(any(KnowledgeQuery.class))).thenThrow(
                new AiProviderException("Límite de cuota superado", AiProviderException.ErrorType.RATE_LIMITED)
        );

        String payload = """
                {
                    "question": "¿Con cuánta anticipación puedo cancelar una reserva?"
                }
                """;

        mvc.perform(post(ASK_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("RateLimited"));
    }
}
