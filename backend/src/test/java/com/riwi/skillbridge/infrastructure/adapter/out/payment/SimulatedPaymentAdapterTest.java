package com.riwi.skillbridge.infrastructure.adapter.out.payment;

import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedPaymentAdapterTest {

    private SimulatedPaymentAdapter adapter;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        adapter = new SimulatedPaymentAdapter();
        bookingId = UUID.randomUUID();
    }

    @Test
    void testApprovePayment() {
        // Prueba de aprobacion determinista (tarjeta valida y CVV normal)
        PaymentCommand command = new PaymentCommand("key-1", bookingId, new BigDecimal("100.00"), "1111", "12/25", "123");
        PaymentResult result = adapter.process(command);

        assertEquals(PaymentStatus.APPROVED, result.status());
        assertEquals("Payment approved", result.message());
        assertNotNull(result.bookingId());
    }

    @Test
    void testDeclineCard0000() {
        // Prueba de rechazo determinista (tarjeta finalizando en 0000)
        PaymentCommand command = new PaymentCommand("key-2", bookingId, new BigDecimal("100.00"), "0000", "12/25", "123");
        PaymentResult result = adapter.process(command);

        assertEquals(PaymentStatus.DECLINED, result.status());
        assertEquals("Card declined deterministically", result.message());
    }

    @Test
    void testSimulateTimeout() {
        // Prueba de timeout simulado (CVV es "TIMEOUT", debe tardar >= 2 segundos)
        PaymentCommand command = new PaymentCommand("key-3", bookingId, new BigDecimal("100.00"), "1111", "12/25", "TIMEOUT");

        long startTime = System.currentTimeMillis();
        PaymentResult result = adapter.process(command);
        long endTime = System.currentTimeMillis();

        assertEquals(PaymentStatus.DECLINED, result.status());
        assertEquals("Simulated timeout", result.message());
        assertTrue((endTime - startTime) >= 2000, "Debe simular un retardo de 2 segundos");
    }

    @Test
    void testSimulateError() {
        // Prueba de excepcion de sistema (CVV es "ERROR")
        PaymentCommand command = new PaymentCommand("key-4", bookingId, new BigDecimal("100.00"), "1111", "12/25", "ERROR");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> adapter.process(command));
        assertEquals("Simulated payment system error", exception.getMessage());
    }
}
