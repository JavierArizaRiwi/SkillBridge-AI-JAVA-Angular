CREATE TABLE categories (
                          id UUID PRIMARY KEY,
                          name VARCHAR(80) NOT NULL UNIQUE,
                          active BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Conserva todas las categorías que ya estaban escritas en offerings.
-- md5 produce una cadena hexadecimal válida para convertirla a UUID.
INSERT INTO categories (id, name)
SELECT md5(normalized)::uuid, upper(normalized)
FROM (
       SELECT DISTINCT lower(trim(category)) AS normalized
       FROM offerings
       WHERE category IS NOT NULL AND trim(category) <> ''
     ) existing_categories;

CREATE INDEX idx_categories_active ON categories(active);
