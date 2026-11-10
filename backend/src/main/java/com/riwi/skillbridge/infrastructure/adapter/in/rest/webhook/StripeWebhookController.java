package com.riwi.skillbridge.infrastructure.adapter.in.rest.webhook;

import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.service.PaymentApprovedAssembler;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de entrada REST para recibir y validar webhooks asíncronos emitidos por Stripe.
 * Utiliza el SDK de Stripe exclusivamente para la validación criptográfica de la firma del webhook.
 */
@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentEventPublisherPort paymentEventPublisherPort;
    private final PaymentApprovedAssembler approvedAssembler;
    private final String webhookSecret;

    public StripeWebhookController(
            PaymentEventPublisherPort paymentEventPublisherPort,
            PaymentApprovedAssembler approvedAssembler,
            @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.paymentEventPublisherPort = paymentEventPublisherPort;
        this.approvedAssembler = approvedAssembler;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("Stripe webhook received without Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Missing Stripe-Signature header"));
        }

        Event event;
        try {
            // Verificación criptográfica obligatoria con el SDK oficial de Stripe
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Stripe signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid webhook signature"));
        } catch (Exception e) {
            log.error("Unexpected error validating Stripe webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Webhook processing failed"));
        }

        log.info("Verified Stripe webhook event received: type={}, id={}", event.getType(), event.getId());

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = dataObjectDeserializer.getObject().orElseGet(() -> {
            try {
                return dataObjectDeserializer.deserializeUnsafe();
            } catch (Exception ex) {
                log.warn("Could not deserialize StripeObject unsafe: {}", ex.getMessage());
                return null;
            }
        });

        if (stripeObject instanceof PaymentIntent paymentIntent) {
            processPaymentIntentEvent(event.getType(), paymentIntent);
        } else {
            log.debug("Event {} does not wrap a PaymentIntent object. Ignored.", event.getType());
        }

        return ResponseEntity.ok(Map.of("received", true));
    }

    private void processPaymentIntentEvent(String eventType, PaymentIntent paymentIntent) {
        Map<String, String> metadata = paymentIntent.getMetadata();
        String bookingIdStr = metadata != null ? metadata.get("bookingId") : null;

        if (bookingIdStr == null || bookingIdStr.isBlank()) {
            log.warn("Stripe PaymentIntent {} metadata does not contain 'bookingId'. Skipping event emission.",
                    paymentIntent.getId());
            return;
        }

        UUID bookingId;
        try {
            bookingId = UUID.fromString(bookingIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID in metadata 'bookingId': {}", bookingIdStr);
            return;
        }

        switch (eventType) {
            case "payment_intent.succeeded" -> {
                log.info("Payment succeeded for bookingId={} (Stripe PaymentIntent={}). Emitting PaymentApproved event.",
                        bookingId, paymentIntent.getId());
                // El webhook de Stripe no aporta un UUID de pago propio: paymentId = null.
                paymentEventPublisherPort.publishApproved(approvedAssembler.assemble(bookingId, null));
            }
            case "payment_intent.payment_failed" -> {
                String reason = paymentIntent.getLastPaymentError() != null && paymentIntent.getLastPaymentError().getMessage() != null
                        ? paymentIntent.getLastPaymentError().getMessage()
                        : "Payment processing failed in Stripe";
                log.info("Payment failed for bookingId={} (Stripe PaymentIntent={}). Reason: {}. Emitting PaymentRejected event.",
                        bookingId, paymentIntent.getId(), reason);
                paymentEventPublisherPort.publishRejected(new PaymentRejected(bookingId, reason));
            }
            default -> log.debug("Event type '{}' for bookingId={} does not trigger domain status changes.",
                    eventType, bookingId);
        }
    }
}
