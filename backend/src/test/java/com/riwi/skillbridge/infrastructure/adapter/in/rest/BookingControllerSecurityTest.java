package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.AuthCookieService;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = BookingController.class,
    properties = "app.cors.allowed-origins=http://localhost:4200")
@Import({SecurityConfiguration.class, RestAccessDeniedHandler.class})
class BookingControllerSecurityTest {

    private static final String CUSTOMER_EMAIL = "customer@test.com";
    private static final UUID BOOKING_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateBookingUseCase createBookingUseCase;
    @MockitoBean
    private ListCustomerBookingsUseCase listCustomerBookingsUseCase;
    @MockitoBean
    private CancelBookingUseCase cancelBookingUseCase;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private AuthCookieService authCookieService;

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void cancela_con_el_email_del_usuario_autenticado() throws Exception {
        when(cancelBookingUseCase.cancel(any())).thenReturn(cancelledBooking());

        mvc.perform(patch(cancelUrl()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        ArgumentCaptor<CancelBookingCommand> command = ArgumentCaptor.forClass(CancelBookingCommand.class);
        verify(cancelBookingUseCase).cancel(command.capture());
        assertThat(command.getValue().bookingId()).isEqualTo(BOOKING_ID);
        assertThat(command.getValue().customerEmail()).isEqualTo(CUSTOMER_EMAIL);
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void segunda_cancelacion_retorna_ok_con_el_estado_cancelled() throws Exception {
        when(cancelBookingUseCase.cancel(any())).thenReturn(cancelledBooking());

        mvc.perform(patch(cancelUrl()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void reserva_ajena_o_inexistente_responde_404() throws Exception {
        when(cancelBookingUseCase.cancel(any()))
            .thenThrow(new DomainNotFoundException("Reserva no encontrada"));

        mvc.perform(patch(cancelUrl()).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void regla_de_negocio_responde_422() throws Exception {
        when(cancelBookingUseCase.cancel(any()))
            .thenThrow(new BusinessRuleException("La reserva debe cancelarse con una anticipación mínima de 24 horas"));

        mvc.perform(patch(cancelUrl()).with(csrf()))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sin_jwt_responde_401_y_no_ejecuta_el_caso_de_uso() throws Exception {
        mvc.perform(patch(cancelUrl()))
            .andExpect(status().isUnauthorized());

        verify(cancelBookingUseCase, never()).cancel(any());
    }

    @Test
    void jwt_alterado_responde_401_y_no_ejecuta_el_caso_de_uso() throws Exception {
        when(jwtService.extractUsername("altered-token"))
            .thenThrow(new MalformedJwtException("Token inválido"));

        mvc.perform(patch(cancelUrl()).header("Authorization", "Bearer altered-token"))
            .andExpect(status().isUnauthorized());

        verify(cancelBookingUseCase, never()).cancel(any());
    }

    @Test
    @WithMockUser(username = CUSTOMER_EMAIL, roles = "CUSTOMER")
    void crear_reserva_sin_idempotency_key_responde_400() throws Exception {
        mvc.perform(post("/api/bookings").with(csrf()).contentType("application/json")
                .content("{\"offeringId\":\"" + UUID.randomUUID() + "\",\"scheduledAt\":\"2030-01-02T12:00:00Z\"}"))
            .andExpect(status().isBadRequest());
        verify(createBookingUseCase, never()).create(any(), any(), any(), any());
    }

    private Booking cancelledBooking() {
        return new Booking(BOOKING_ID, UUID.randomUUID(), UUID.randomUUID(),
            Instant.parse("2030-01-01T12:00:00Z"), BookingStatus.CANCELLED, 1);
    }

    private String cancelUrl() {
        return "/api/bookings/" + BOOKING_ID + "/cancel";
    }
}
