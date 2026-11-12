ALTER TABLE bookings ADD COLUMN idempotency_key VARCHAR(255);
ALTER TABLE bookings ADD COLUMN idempotency_request_hash VARCHAR(64);
CREATE UNIQUE INDEX uq_booking_customer_idempotency_key
    ON bookings(customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
