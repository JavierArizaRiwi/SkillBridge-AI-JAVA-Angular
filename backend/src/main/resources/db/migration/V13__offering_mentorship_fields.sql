-- Datos de mentoría de la oferta, transportados en PaymentApproved v2
-- (docs/contracts/mentorships/events.md). Las ofertas existentes quedan ASYNCHRONOUS / 1 hora.
ALTER TABLE offerings ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'ASYNCHRONOUS';
ALTER TABLE offerings ADD COLUMN duration_hours INTEGER NOT NULL DEFAULT 1;
ALTER TABLE offerings ADD CONSTRAINT chk_offerings_mode CHECK (mode IN ('ASYNCHRONOUS', 'LIVE'));
ALTER TABLE offerings ADD CONSTRAINT chk_offerings_duration CHECK (duration_hours BETWEEN 1 AND 24);
