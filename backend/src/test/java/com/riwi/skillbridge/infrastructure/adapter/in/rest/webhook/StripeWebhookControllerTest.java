package com.riwi.skillbridge.infrastructure.adapter.in.rest.webhook;

import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private PaymentEventPublisherPort paymentEventPublisherPort;

    @Mock
    private com.riwi.skillbridge.application.service.PaymentApprovedAssembler approvedAssembler;

    private StripeWebhookController controller;
    private final String webhookSecret = "whsec_test_secret_key_12345";

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(paymentEventPublisherPort, approvedAssembler, webhookSecret);
    }

    private String generateSignature(String payload, String secret) throws Exception {
        long timestamp = Webhook.Util.getTimeNow();
        String payloadToSign = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(secret, payloadToSign);
        return "t=" + timestamp + ",v1=" + signature;
    }

    @Test
    void testMissingSignatureReturnsBadRequest() {
        ResponseEntity<Map<String, Object>> response = controller.handleStripeWebhook("{}", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
        verifyNoInteractions(paymentEventPublisherPort);
    }

    @Test
    void testInvalidSignatureReturnsBadRequest() {
        String payload = "{\"id\":\"evt_123\",\"type\":\"payment_intent.succeeded\"}";
        String invalidSig = "t=123456789,v1=bad_signature_hash";

        ResponseEntity<Map<String, Object>> response = controller.handleStripeWebhook(payload, invalidSig);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid webhook signature", response.getBody().get("error"));
        verifyNoInteractions(paymentEventPublisherPort);
    }

    @Test
    void testPaymentIntentSucceededPublishesPaymentApproved() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(approvedAssembler.assemble(bookingId, null)).thenReturn(new PaymentApproved(bookingId));
        String payload = """
        {
          "id": "evt_test_succeeded_1",
          "object": "event",
          "api_version": "2024-06-20",
          "type": "payment_intent.succeeded",
          "data": {
            "object": {
              "id": "pi_test_999",
              "object": "payment_intent",
              "amount": 5000,
              "currency": "usd",
              "status": "succeeded",
              "metadata": {
                "bookingId": "%s"
              }
            }
          }
        }
        """.formatted(bookingId);

        String validSig = generateSignature(payload, webhookSecret);

        ResponseEntity<Map<String, Object>> response = controller.handleStripeWebhook(payload, validSig);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("received"));

        ArgumentCaptor<PaymentApproved> captor = ArgumentCaptor.forClass(PaymentApproved.class);
        verify(paymentEventPublisherPort).publishApproved(captor.capture());
        assertEquals(bookingId, captor.getValue().bookingId());
        verify(paymentEventPublisherPort, never()).publishRejected(any());
    }

    @Test
    void testPaymentIntentFailedPublishesPaymentRejected() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String payload = """
        {
          "id": "evt_test_failed_1",
          "object": "event",
          "api_version": "2024-06-20",
          "type": "payment_intent.payment_failed",
          "data": {
            "object": {
              "id": "pi_test_888",
              "object": "payment_intent",
              "amount": 5000,
              "currency": "usd",
              "status": "requires_payment_method",
              "last_payment_error": {
                "message": "Your card was declined."
              },
              "metadata": {
                "bookingId": "%s"
              }
            }
          }
        }
        """.formatted(bookingId);

        String validSig = generateSignature(payload, webhookSecret);

        ResponseEntity<Map<String, Object>> response = controller.handleStripeWebhook(payload, validSig);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("received"));

        ArgumentCaptor<PaymentRejected> captor = ArgumentCaptor.forClass(PaymentRejected.class);
        verify(paymentEventPublisherPort).publishRejected(captor.capture());
        assertEquals(bookingId, captor.getValue().bookingId());
        assertEquals("Your card was declined.", captor.getValue().reason());
        verify(paymentEventPublisherPort, never()).publishApproved(any());
    }

    @Test
    void testEventWithoutBookingIdDoesNotPublishDomainEvent() throws Exception {
        String payload = """
        {
          "id": "evt_test_no_meta",
          "object": "event",
          "api_version": "2024-06-20",
          "type": "payment_intent.succeeded",
          "data": {
            "object": {
              "id": "pi_test_777",
              "object": "payment_intent",
              "amount": 2500,
              "currency": "usd",
              "status": "succeeded",
              "metadata": {}
            }
          }
        }
        """;

        String validSig = generateSignature(payload, webhookSecret);

        ResponseEntity<Map<String, Object>> response = controller.handleStripeWebhook(payload, validSig);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("received"));
        verifyNoInteractions(paymentEventPublisherPort);
    }
}
