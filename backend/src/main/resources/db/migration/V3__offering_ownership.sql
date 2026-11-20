-- 1. Nueva columna, nullable al inicio para poder rellenarla
ALTER TABLE offerings ADD COLUMN provider_id UUID;

-- 2. Backfill: los offerings existentes pasan al Provider del seed
UPDATE offerings
SET provider_id = 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'
WHERE provider_id IS NULL;

-- 3. Obligatoria, con FK e índice
ALTER TABLE offerings ALTER COLUMN provider_id SET NOT NULL;
ALTER TABLE offerings
  ADD CONSTRAINT fk_offerings_provider
    FOREIGN KEY (provider_id) REFERENCES app_users(id);
CREATE INDEX idx_offerings_provider ON offerings(provider_id);
