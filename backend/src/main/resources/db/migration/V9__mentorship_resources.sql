CREATE TABLE mentorship_resources (
    id UUID PRIMARY KEY,
    offering_id UUID NOT NULL REFERENCES offerings(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    url VARCHAR(1500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mentorship_resources_offering ON mentorship_resources(offering_id, created_at);
