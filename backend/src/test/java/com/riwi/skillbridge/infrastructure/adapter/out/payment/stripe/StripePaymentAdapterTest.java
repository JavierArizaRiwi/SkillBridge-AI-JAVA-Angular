package com.riwi.skillbridge.infrastructure.adapter.out.payment.stripe;

import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Conector con Stripe")
class StripePaymentAdapterTest {

    @Mock
    private StripeFeignClient stripeFeignClient;

    private StripePaymentAdapter adapter;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        adapter = new StripePaymentAdapter(stripeFeignClient, "usd");
        bookingId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Caso Feliz: Crea PaymentIntent en Stripe y retorna el clientSecret")
    void testProcessCreatesPaymentIntentSuccessfully() {
        // [Caso Feliz]: Pasa el monto a centavos (49.99 -> 4999), envía metadata y devuelve el secret para la tarjeta.
        String idempotencyKey = "idem-key-stripe-1";
        BigDecimal amount = new BigDecimal("49.99");
        PaymentCommand command = new PaymentCommand(idempotencyKey, bookingId, amount);

        StripePaymentIntentResponse feignResponse = new StripePaymentIntentResponse(
                "pi_test_123",
                "payment_intent",
                4999L,
                "usd",
                "requires_payment_method",
                "pi_test_123_secret_xyz",
                null,
                Map.of("bookingId", bookingId.toString(), "idempotencyKey", idempotencyKey)
        );

        when(stripeFeignClient.createPaymentIntent(eq(idempotencyKey), any(MultiValueMap.class)))
                .thenReturn(feignResponse);

        PaymentResult result = adapter.process(command);

        assertNotNull(result);
        assertEquals(bookingId, result.bookingId());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertEquals("pi_test_123_secret_xyz", result.clientSecret());
        assertTrue(result.message().contains("pi_test_123"));

        ArgumentCaptor<MultiValueMap<String, String>> captor = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(stripeFeignClient).createPaymentIntent(eq(idempotencyKey), captor.capture());

        MultiValueMap<String, String> capturedParams = captor.getValue();
        assertEquals("4999", capturedParams.getFirst("amount"));
        assertEquals("usd", capturedParams.getFirst("currency"));
        assertEquals(bookingId.toString(), capturedParams.getFirst("metadata[bookingId]"));
        assertEquals(idempotencyKey, capturedParams.getFirst("metadata[idempotencyKey]"));
        assertEquals("true", capturedParams.getFirst("automatic_payment_methods[enabled]"));
    }

    @Test
    @DisplayName("Caso Feliz: Pago aprobado de inmediato con estado 'succeeded'")
    void testProcessWithSucceededStatus() {
        // [Caso Feliz]: Si Stripe confirma el pago de inmediato, marcamos el estado como APPROVED.
        String idempotencyKey = "idem-key-stripe-2";
        PaymentCommand command = new PaymentCommand(idempotencyKey, bookingId, new BigDecimal("10.00"));

        StripePaymentIntentResponse feignResponse = new StripePaymentIntentResponse(
                "pi_test_456",
                "payment_intent",
                1000L,
                "usd",
                "succeeded",
                "pi_test_456_secret_abc",
                null,
                Map.of("bookingId", bookingId.toString())
        );

        when(stripeFeignClient.createPaymentIntent(eq(idempotencyKey), any(MultiValueMap.class)))
                .thenReturn(feignResponse);

        PaymentResult result = adapter.process(command);

        assertEquals(PaymentStatus.APPROVED, result.status());
        assertEquals("pi_test_456_secret_abc", result.clientSecret());
    }

    @Test
    @DisplayName("Caso Triste: Si Stripe se cae o da timeout, responde con fallo seguro")
    void testProcessHandlesExceptionGracefully() {
        // [Caso Triste]: Si falla la conexión con Stripe, atrapa el error y devuelve DECLINED sin tumbar la app.
        PaymentCommand command = new PaymentCommand("idem-fail", bookingId, new BigDecimal("75.00"));
        when(stripeFeignClient.createPaymentIntent(eq("idem-fail"), any(MultiValueMap.class)))
                .thenThrow(new RuntimeException("Stripe API connection timed out"));

        PaymentResult result = adapter.process(command);

        assertNotNull(result);
        assertEquals(bookingId, result.bookingId());
        assertEquals(PaymentStatus.DECLINED, result.status());
        assertNull(result.clientSecret());
        assertTrue(result.message().contains("Payment gateway error"));
        assertTrue(result.message().contains("Stripe API connection timed out"));
    }
}
