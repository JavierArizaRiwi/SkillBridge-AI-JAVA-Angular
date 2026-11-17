CREATE TABLE mentorship_assignments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    customer_id UUID NOT NULL REFERENCES app_users(id),
    offering_id UUID NOT NULL REFERENCES offerings(id),
    mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    access_starts_at TIMESTAMPTZ NOT NULL,
    access_ends_at TIMESTAMPTZ NOT NULL,
    progress_percent INTEGER NOT NULL DEFAULT 0 CHECK (progress_percent BETWEEN 0 AND 100),
    live_room_url VARCHAR(1500),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_assignment_booking UNIQUE (booking_id),
    CONSTRAINT chk_assignment_window CHECK (access_ends_at > access_starts_at)
);
CREATE INDEX idx_mentorship_assignments_customer ON mentorship_assignments(customer_id, access_starts_at DESC);
