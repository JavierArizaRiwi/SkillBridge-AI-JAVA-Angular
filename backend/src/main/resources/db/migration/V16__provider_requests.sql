CREATE TABLE IF NOT EXISTS provider_requests (
                                 id              UUID PRIMARY KEY,
                                 user_id         UUID NOT NULL REFERENCES app_users (id),
                                 status          VARCHAR(20) NOT NULL,
                                 message         VARCHAR(500),
                                 created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                 decided_by      UUID REFERENCES app_users (id),
                                 decided_at      TIMESTAMP WITH TIME ZONE,
                                 decision_reason VARCHAR(500),
                                 CONSTRAINT ck_provider_requests_status
                                   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- A lo sumo una solicitud PENDING por usuario, incluso con peticiones simultáneas
CREATE UNIQUE INDEX IF NOT EXISTS uq_provider_requests_pending
  ON provider_requests (user_id) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_provider_requests_status_created
  ON provider_requests (status, created_at);
