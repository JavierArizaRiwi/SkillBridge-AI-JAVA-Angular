# Evidencia local: omisiones del inventario

- Fecha UTC: 2026-10-21T18:58:17+00:00
- Rama: develop
- Commit auditado: cdd009aa8c9ba351fa8bb50bb1ad0a381e259793

## 1. Estado de Git
```text
AM docs/audit/findings.md
A  scripts/repo_audit.py
?? docs/audit/evidence/
?? docs/audit/generated/
```

## 2. Regla de exclusión del script
```text
20:EXCLUDED_DIRS = {
108:def walk_files(root: Path, out_dir: Path, max_files: int) -> tuple[list[Path], bool]:
116:            if d in EXCLUDED_DIRS:
127:        dirs[:] = filtered
204:    files, truncated = walk_files(root, out_dir, max(1, args.max_files))
```

## 3. Adaptadores rastreados por Git frente al inventario
Archivos rastreados bajo adapter/out o port/out: 78
Rutas registradas en CSV: 293
Archivos adapter/out o port/out ausentes del CSV: 78
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/AiRecommendationPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/AiStructuredResponse.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/BookingEventPublisherPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/BookingRepositoryPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/BookingStatusHistoryPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/CategoryRepositoryPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationMessage.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationPublisherPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationSenderPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/NotificationType.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/OfferingCachePort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/OfferingRepositoryPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/PasswordHasherPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/PaymentEventPublisherPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/PaymentPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/PaymentResultRepositoryPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/PermanentNotificationException.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/SpeechToTextPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/TextToSpeechPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/TokenPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/UserAccountPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/UserAdminPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/UserRepositoryPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/VisionPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/AuditEventPublisherPort.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BookingCancelledPayload.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BookingCreatedPayload.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BusinessEvent.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/PaymentApprovedPayload.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/PaymentRejectedPayload.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/RecommendationGeneratedPayload.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/application/port/out/event/RecommendationRequestedPayload.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/ElevenLabsTextToSpeechAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiSpeechToTextAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiVisionAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/NoOpTextToSpeechAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSender.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/SimulatedNotificationSender.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/InMemoryPaymentResultRepositoryAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignClient.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignConfig.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentIntentResponse.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingStatusHistoryPersistenceAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/CategoryPersistenceAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/OfferingPersistenceAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/UserPersistenceAdapter.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingEntity.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingStatusHistoryEntity.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/CategoryEntity.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/OfferingEntity.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/UserEntity.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingRepository.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingStatusHistoryRepository.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaCategoryRepository.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaOfferingRepository.java
  AUSENTE: backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaUserRepository.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/application/port/out/NotificationMessageTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapterTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapterTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/MockAiAdapter.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapterTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSenderTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapterTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapterTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java
  AUSENTE: backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java

## Símbolos clave en el índice Java
UserPersistenceAdapter: AUSENTE
JpaUserRepository: PRESENTE
AuditEventRepository: AUSENTE
RedisOfferingCacheAdapter: AUSENTE
KafkaAuditEventPublisher: AUSENTE
StripeFeignClient: AUSENTE

## 4. Huella del script auditado
502a369be6bb5a3e67e252903182349c6484dd6b1c371e9018fa179695f59a50  scripts/repo_audit.py
