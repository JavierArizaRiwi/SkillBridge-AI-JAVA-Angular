ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE booking_status_history (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by UUID NOT NULL REFERENCES app_users(id),
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_booking_status_history_transition
    ON booking_status_history(booking_id, previous_status, new_status);
CREATE INDEX idx_booking_status_history_booking
    ON booking_status_history(booking_id);
