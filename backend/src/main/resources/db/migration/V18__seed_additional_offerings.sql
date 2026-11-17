-- Seed de ofertas adicionales (originalmente en V1, movido a V18 porque V1 ya estaba aplicada en produccion
-- con otro checksum; modificar una migracion aplicada rompe Flyway validate y tumba el arranque).
-- provider_id: proveedor seed (mismo backfill que V3). Esta migracion corre despues de V3.
-- Idempotente: ON CONFLICT evita duplicados si la fila ya existe.
INSERT INTO offerings (id, title, description, category, price, active, provider_id) VALUES
('44444444-4444-4444-4444-444444444444', 'Mentoría React & Next.js', 'Desarrollo de aplicaciones modernas con Server Components y Next.js.', 'FRONTEND', 80000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('55555555-5555-5555-5555-555555555555', 'Mentoría Node.js & NestJS', 'Microservicios escalables con TypeScript, colas y NestJS.', 'BACKEND', 78000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('66666666-6666-6666-6666-666666666666', 'Mentoría Python FastAPI & Django', 'Diseño de APIs asíncronas con FastAPI y desarrollo web con Django.', 'BACKEND', 82000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('77777777-7777-7777-7777-777777777777', 'Kubernetes & Contenedores en Producción', 'Orquestación de microservicios con Docker, Helm y Kubernetes en producción.', 'CLOUD', 130000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('88888888-8888-8888-8888-888888888888', 'Mentoría Vue 3 & Pinia', 'Desarrollo reactivo con Composition API, TypeScript y Pinia.', 'FRONTEND', 70000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('99999999-9999-9999-9999-999999999999', 'Arquitectura AWS Cloud & Terraform', 'Infraestructura como código en AWS con Terraform y buenas prácticas.', 'CLOUD', 115000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Mentoría Go (Golang) Microservicios', 'Construcción de servicios concurrentes y de alto rendimiento con Go.', 'BACKEND', 95000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Mentoría .NET Core & C#', 'APIs REST con .NET 8, Clean Architecture y Entity Framework.', 'BACKEND', 88000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Desarrollo Móvil con Flutter', 'Aplicaciones móviles multiplataforma en iOS y Android con Flutter y Dart.', 'MOBILE', 85000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Desarrollo Móvil con React Native', 'Construcción de apps móviles nativas con React Native y Expo.', 'MOBILE', 80000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Desarrollo Android Nativo con Kotlin', 'Jetpack Compose, coroutines, arquitectura MVVM y mejores prácticas en Android.', 'MOBILE', 90000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('ffffffff-ffff-ffff-ffff-ffffffffffff', 'Desarrollo iOS con Swift & SwiftUI', 'Aplicaciones nativas para iOS con SwiftUI y arquitectura modular.', 'MOBILE', 95000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('12121212-1212-1212-1212-121212121212', 'Optimización y Tuning de PostgreSQL', 'Optimización de consultas complejas, índices avanzados y planes EXPLAIN.', 'BACKEND', 92000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('13131313-1313-1313-1313-131313131313', 'CI/CD y DevOps con GitHub Actions', 'Automatización de pruebas, pipelines de despliegue y flujos GitOps.', 'CLOUD', 75000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('14141414-1414-1414-1414-141414141414', 'Seguridad de Aplicaciones Web (OWASP)', 'Auditoría y mitigación de vulnerabilidades OWASP Top 10 y hardening web.', 'BACKEND', 105000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('15151515-1515-1515-1515-151515151515', 'Machine Learning & IA con Python', 'Modelos predictivos con Scikit-Learn, procesamiento de datos y evaluación de modelos.', 'CLOUD', 110000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c'),
('16161616-1616-1616-1616-161616161616', 'Inteligencia Artificial Generativa & RAG', 'Integración de modelos LLM con embeddings vectoriales, RAG y Spring AI.', 'BACKEND', 125000, true, 'f6e5d4c3-b2a1-0f9e-8d7c-6b5a4f3e2d1c')
ON CONFLICT (id) DO NOTHING;

-- Datos de tendencias para las ofertas nuevas (mismo modelo que V17).
-- V17 corre antes que esta migracion, asi que en una base nueva no pudo sembrar estas ofertas
-- (su WHERE EXISTS las descarta). Idempotente: ON CONFLICT conserva metricas reales ya registradas.
INSERT INTO recommendation_daily_metrics
    (id, date, offering_id, recommendations, unique_users, bookings, cancellations,
     conversion_rate, trend_score, forecast_next_7_days)
SELECT gen_random_uuid(),
       CURRENT_DATE - d,
       p.offering_id,
       v.recs,
       v.recs / 2 + v.bks,
       v.bks,
       c.canc,
       CASE WHEN v.recs > 0 THEN GREATEST(v.bks - c.canc, 0)::double precision / v.recs ELSE 0 END,
       0,
       0
FROM (VALUES
    -- offering_id, recs/day previous 7d, recs/day recent 7d, bookings/day previous, bookings/day recent
    ('44444444-4444-4444-4444-444444444444'::uuid,  5,  9, 1, 2),  -- React & Next.js: growing
    ('55555555-5555-5555-5555-555555555555'::uuid,  6,  8, 1, 2),  -- Node.js & NestJS
    ('66666666-6666-6666-6666-666666666666'::uuid,  4,  6, 1, 1),  -- Python FastAPI & Django
    ('77777777-7777-7777-7777-777777777777'::uuid,  5,  9, 0, 2),  -- Kubernetes: new demand
    ('88888888-8888-8888-8888-888888888888'::uuid,  3,  3, 0, 1),  -- Vue 3 & Pinia: stable
    ('99999999-9999-9999-9999-999999999999'::uuid,  5,  7, 1, 1),  -- AWS & Terraform
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,  2,  5, 0, 1),  -- Go microservices: rising
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid,  3,  4, 1, 1),  -- .NET Core & C#
    ('cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid,  9,  5, 3, 1),  -- Flutter: declining
    ('dddddddd-dddd-dddd-dddd-dddddddddddd'::uuid,  6,  7, 1, 1),  -- React Native
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'::uuid,  4,  6, 1, 1),  -- Android Kotlin
    ('ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid,  3,  3, 1, 1),  -- iOS Swift: stable
    ('12121212-1212-1212-1212-121212121212'::uuid,  6,  6, 2, 2),  -- PostgreSQL tuning: flat
    ('13131313-1313-1313-1313-131313131313'::uuid,  4,  7, 1, 2),  -- CI/CD GitHub Actions
    ('14141414-1414-1414-1414-141414141414'::uuid,  3,  6, 0, 1),  -- OWASP security
    ('15151515-1515-1515-1515-151515151515'::uuid,  4,  8, 1, 2),  -- ML & AI with Python
    ('16161616-1616-1616-1616-161616161616'::uuid,  4, 12, 1, 3)   -- Generative AI & RAG: rising fast
) AS p(offering_id, recs_prev, recs_recent, bks_prev, bks_recent)
CROSS JOIN generate_series(0, 13) AS d
CROSS JOIN LATERAL (SELECT
        (CASE WHEN d <= 6 THEN p.recs_recent ELSE p.recs_prev END) + (d % 3) AS recs,
        CASE WHEN d <= 6 THEN p.bks_recent ELSE p.bks_prev END AS bks
) AS v
CROSS JOIN LATERAL (SELECT CASE WHEN d % 6 = 0 AND v.bks > 1 THEN 1 ELSE 0 END AS canc) AS c
WHERE EXISTS (SELECT 1 FROM offerings o WHERE o.id = p.offering_id)
ON CONFLICT (offering_id, date) DO NOTHING;
