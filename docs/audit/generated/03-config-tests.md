# Configuración, dependencias y pruebas

## Archivos de construcción / CI

- `.github/workflows/ci.yml`
- `.github/workflows/qodana_code_quality.yml`
- `Makefile`
- `audit-service/backend/Dockerfile`
- `audit-service/backend/pom.xml`
- `audit-service/frontend/Dockerfile`
- `audit-service/frontend/angular.json`
- `audit-service/frontend/package.json`
- `backend/Dockerfile`
- `backend/pom.xml`
- `frontend/Dockerfile`
- `frontend/angular.json`
- `frontend/package.json`

## Configuración detectada

- `.github/workflows/ci.yml`
- `.github/workflows/qodana_code_quality.yml`
- `audit-service/backend/src/main/resources/application.yml`
- `audit-service/frontend/angular.json`
- `audit-service/frontend/tsconfig.app.json`
- `audit-service/frontend/tsconfig.json`
- `audit-service/frontend/tsconfig.spec.json`
- `backend/src/main/resources/application.yml`
- `docker-compose.yml`
- `frontend/angular.json`
- `frontend/tsconfig.app.json`
- `frontend/tsconfig.json`
- `frontend/tsconfig.spec.json`

## Manifiestos npm y dependencias de runtime

- `audit-service/frontend/package.json`: paquete `frontend`, 9 dependencias de runtime y 15 de desarrollo. Runtime: `@angular/common`, `@angular/compiler`, `@angular/core`, `@angular/forms`, `@angular/platform-browser`, `@angular/router`, `rxjs`, `tslib`, `zone.js`.
- `frontend/package.json`: paquete `skillbridge-ai`, 15 dependencias de runtime y 11 de desarrollo. Runtime: `@angular/animations`, `@angular/common`, `@angular/compiler`, `@angular/core`, `@angular/forms`, `@angular/platform-browser`, `@angular/platform-browser-dynamic`, `@angular/router`, `@stripe/stripe-js`, `@tailwindcss/postcss`, `postcss`, `rxjs`, `tailwindcss`, `tslib`, `zone.js`.

## Pruebas detectadas (por nombre o carpeta)

- `audit-service/backend/src/test/java/com/riwi/audit/repository/AuditEventRepositoryTest.java`
- `audit-service/frontend/src/app/app.spec.ts`
- `backend/src/test/java/com/riwi/skillbridge/application/port/in/CancelBookingCommandTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/port/out/NotificationMessageTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/recommendation/RecommendationContractTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/AiRecommendationServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/AuthServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/BookingServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/NotificationServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/OfferingCacheConsistencyTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/OfferingCommandServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/OfferingQueryServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/OfferingServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/PaymentAuthorizationServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/application/service/UserAdminServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/domain/model/BookingTest.java`
- `backend/src/test/java/com/riwi/skillbridge/domain/policy/OfferingAccessPolicyTest.java`
- `backend/src/test/java/com/riwi/skillbridge/domain/policy/UserManagementPolicyTest.java`
- `backend/src/test/java/com/riwi/skillbridge/domain/service/BookingCancellationPolicyTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumerTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserControllerTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingControllerSecurityTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingSecurityTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/filter/CorrelationIdFilterTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/validation/MediaFileValidatorTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookControllerTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapterTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapterTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/MockAiAdapter.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapterTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSenderTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapterTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapterTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/security/DatabaseUserDetailsServiceTest.java`
- `backend/src/test/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilterTest.java`
- `frontend/src/app/core/stripe.service.spec.ts`
- `frontend/src/app/features/payment-modal/payment-modal.spec.ts`
- `frontend/src/app/guards/auth-guard.spec.ts`

## Migraciones de base de datos detectadas

- `audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java`
- `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AdminSeeder.java`
- `backend/src/main/resources/db/migration/V1__init.sql`
- `backend/src/main/resources/db/migration/V2__seed_data.sql`
- `backend/src/main/resources/db/migration/V3__offering_ownership.sql`
- `backend/src/main/resources/db/migration/V4__booking_cancellation_audit.sql`
- `backend/src/main/resources/db/migration/V5__user_status.sql`
- `backend/src/main/resources/db/migration/V6__categories.sql`
- `backend/src/main/resources/db/migration/V7__booking_idempotency.sql`

## README / documentación de entrada

- `README.md`
- `audit-service/frontend/README.md`

## Advertencias

- No se imprimen contenidos de archivos `.env`, certificados, claves u otros archivos identificados como sensibles.
- No se leen archivos de lockfile para evitar índices largos y poco útiles.
- La presencia de un archivo no garantiza que esté actualizado ni que el build funcione.
