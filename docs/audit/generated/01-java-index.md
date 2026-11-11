# Índice Java / Spring

Este archivo contiene líneas indicadoras y ubicaciones, no volcados completos de código.

## Spring entry points / layers — 91 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/backend/src/main/java/com/riwi/audit/AuditServiceApplication.java | 6 | @SpringBootApplication |
| audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java | 28 | @Component |
| audit-service/backend/src/main/java/com/riwi/audit/config/CorsConfig.java | 10 | @Configuration |
| audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java | 11 | @Component |
| audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java | 17 | @RestController |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 336 | @Bean |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/MockAiAdapter.java | 12 | @Component |
| backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java | 10 | @SpringBootApplication |
| backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java | 11 | @EnableFeignClients |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/MediaUploadProperties.java | 4 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 36 | @Configuration |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 45 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 96 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 101 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AdminSeeder.java | 12 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 13 | @Configuration |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 29 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 33 | @Bean TopicExchange bookingExchange() { return new TopicExchange(BOOKING_EXCHANGE, true, false); } |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 34 | @Bean TopicExchange paymentExchange() { return new TopicExchange("payment.events", true, false); } |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 35 | @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); } |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 37 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 42 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 50 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 55 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 61 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 67 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 74 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java | 79 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/BookingCancellationConfiguration.java | 11 | @Configuration |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/BookingCancellationConfiguration.java | 13 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/BookingCancellationConfiguration.java | 18 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AiProviderProperties.java | 6 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/DomainConfig.java | 8 | @Configuration |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/DomainConfig.java | 11 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/DomainConfig.java | 16 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 22 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminOfferingController.java | 12 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java | 24 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 19 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 15 | @RestControllerAdvice |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java | 18 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java | 11 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java | 25 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 21 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/validation/MediaFileValidator.java | 7 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/filter/CorrelationIdFilter.java | 18 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookController.java | 26 | @RestController |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaAuditConsumer.java | 11 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java | 14 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumer.java | 13 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java | 15 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapter.java | 17 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/NoOpTextToSpeechAdapter.java | 8 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapter.java | 20 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiSpeechToTextAdapter.java | 12 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java | 16 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/ElevenLabsTextToSpeechAdapter.java | 16 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiVisionAdapter.java | 10 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/OfferingPersistenceAdapter.java | 13 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/CategoryPersistenceAdapter.java | 13 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapter.java | 17 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingStatusHistoryPersistenceAdapter.java | 9 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/UserPersistenceAdapter.java | 20 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/InMemoryPaymentResultRepositoryAdapter.java | 10 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapter.java | 10 | @Component("simulatedPaymentAdapter") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignConfig.java | 16 | @Bean |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapter.java | 22 | @Component("stripePaymentAdapter") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/SimulatedNotificationSender.java | 10 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSender.java | 16 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java | 13 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java | 9 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java | 13 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java | 14 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/SpringPasswordHasherAdapter.java | 7 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/AuthCookieService.java | 13 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/CurrentActorResolver.java | 14 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/RestAccessDeniedHandler.java | 15 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java | 17 | @Service |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java | 15 | @Component |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/DatabaseUserDetailsService.java | 8 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/AiRecommendationService.java | 20 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/TextToSpeechService.java | 9 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/OfferingQueryService.java | 14 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/CategoryService.java | 16 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java | 34 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/NotificationService.java | 8 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/UserAdminService.java | 23 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/OfferingCommandService.java | 22 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/PaymentAuthorizationService.java | 16 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java | 21 | @Service |
| backend/src/main/java/com/riwi/skillbridge/application/service/OfferingService.java | 11 | @Service |

## HTTP mapping annotations — 52 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java | 18 | @RequestMapping("/api/audit") |
| audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java | 24 | @GetMapping("/events") |
| audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java | 29 | @GetMapping("/events/{eventId}") |
| audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java | 36 | @GetMapping("/stats") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 23 | @RequestMapping("/api/offerings") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 47 | @GetMapping |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 53 | @GetMapping("/me") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 64 | @PostMapping |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 79 | @PutMapping("/{id}") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 95 | @PostMapping("/{id}/deactivate") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 108 | @PostMapping("/{id}/activate") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminOfferingController.java | 13 | @RequestMapping("/api/admin/offerings") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminOfferingController.java | 23 | @GetMapping |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java | 25 | @RequestMapping("/api/ai") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java | 48 | @PostMapping("/recommendations") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java | 53 | @PostMapping(value = "/recommendations/voice", consumes = "multipart/form-data") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java | 59 | @PostMapping(value = "/recommendations/image", consumes = "multipart/form-data") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java | 65 | @PostMapping(value = "/tts", produces = "audio/mpeg") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 20 | @RequestMapping("/api/categories") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 38 | @GetMapping |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 44 | @PostMapping |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 52 | @PutMapping("/{id}") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 59 | @DeleteMapping("/{id}") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 17 | @ExceptionHandler(DomainNotFoundException.class) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 24 | @ExceptionHandler(BusinessRuleException.class) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 31 | @ExceptionHandler(InvalidCredentialsException.class) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 38 | @ExceptionHandler(MethodArgumentNotValidException.class) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 48 | @ExceptionHandler(ForbiddenOperationException.class) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 55 | @ExceptionHandler(IllegalArgumentException.class) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 61 | @ExceptionHandler(MaxUploadSizeExceededException.class) ProblemDetail fileTooLarge(MaxUploadSizeExceededException ex) { ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStat... |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 62 | @ExceptionHandler(InvalidMediaFileException.class) ProblemDetail media(InvalidMediaFileException ex) { ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex... |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 63 | @ExceptionHandler(AiProviderException.class) ProblemDetail ai(AiProviderException ex) { ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"El prove... |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java | 64 | @ExceptionHandler(IdempotencyKeyConflictException.class) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java | 19 | @RequestMapping("/api/bookings") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java | 35 | @PostMapping |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java | 44 | @GetMapping("/me") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java | 51 | @PatchMapping("/{bookingId}/cancel") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java | 12 | @RequestMapping("/api/payments") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java | 21 | @PostMapping("/authorize") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java | 26 | @RequestMapping("/api/admin/users") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java | 50 | @GetMapping |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java | 61 | @GetMapping("/{id}") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java | 68 | @PutMapping("/{id}/status") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java | 80 | @PutMapping("/{id}/role") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 22 | @RequestMapping("/api/auth") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 34 | @PostMapping("/register") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 40 | @PostMapping("/login") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 46 | @PostMapping("/logout") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 53 | @GetMapping("/me") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookController.java | 27 | @RequestMapping("/api/webhooks") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookController.java | 42 | @PostMapping("/stripe") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignClient.java | 20 | @PostMapping( |

## HTTP clients / OpenFeign — 4 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java | 11 | @EnableFeignClients |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignClient.java | 13 | @FeignClient( |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignConfig.java | 3 | import feign.RequestInterceptor; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignConfig.java | 17 | public RequestInterceptor stripeAuthRequestInterceptor() { |

## Persistence / transactions — 35 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java | 8 | import org.springframework.data.mongodb.core.MongoTemplate; |
| audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java | 23 | * garantizar que el {@link MongoTemplate}/driver ya esté completamente |
| audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java | 34 | private final MongoTemplate mongoTemplate; |
| audit-service/backend/src/main/java/com/riwi/audit/repository/AuditEventRepository.java | 4 | import org.springframework.data.mongodb.repository.MongoRepository; |
| audit-service/backend/src/main/java/com/riwi/audit/repository/AuditEventRepository.java | 14 | public interface AuditEventRepository extends MongoRepository<BusinessEvent, UUID> { |
| audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java | 24 | @Document(collection = "audit_events") |
| audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java | 26 | @Id |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaOfferingRepository.java | 4 | import org.springframework.data.jpa.repository.JpaRepository; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaOfferingRepository.java | 8 | public interface JpaOfferingRepository extends JpaRepository<OfferingEntity, UUID> { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingStatusHistoryRepository.java | 4 | import org.springframework.data.jpa.repository.JpaRepository; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingStatusHistoryRepository.java | 8 | public interface JpaBookingStatusHistoryRepository extends JpaRepository<BookingStatusHistoryEntity, UUID> { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaUserRepository.java | 4 | import org.springframework.data.jpa.repository.JpaRepository; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaUserRepository.java | 8 | public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaCategoryRepository.java | 4 | import org.springframework.data.jpa.repository.JpaRepository; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaCategoryRepository.java | 9 | public interface JpaCategoryRepository extends JpaRepository<CategoryEntity, UUID> { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingRepository.java | 7 | import org.springframework.data.jpa.repository.JpaRepository; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingRepository.java | 14 | public interface JpaBookingRepository extends JpaRepository<BookingEntity, UUID> { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaBookingRepository.java | 19 | @Query("select booking from BookingEntity booking where booking.id = :bookingId") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/UserEntity.java | 9 | @Entity |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/UserEntity.java | 10 | @Table(name = "app_users") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/UserEntity.java | 12 | @Id |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingStatusHistoryEntity.java | 14 | @Entity |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingStatusHistoryEntity.java | 15 | @Table(name = "booking_status_history") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingStatusHistoryEntity.java | 17 | @Id |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/CategoryEntity.java | 7 | @Entity |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/CategoryEntity.java | 8 | @Table(name = "categories") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/CategoryEntity.java | 10 | @Id |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/OfferingEntity.java | 8 | @Entity |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/OfferingEntity.java | 9 | @Table(name = "offerings") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/OfferingEntity.java | 11 | @Id |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingEntity.java | 8 | @Entity |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingEntity.java | 9 | @Table(name = "bookings") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/BookingEntity.java | 11 | @Id |
| backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java | 53 | @Transactional |
| backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java | 124 | @Transactional |

## Cache / Redis — 3 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java | 8 | import org.springframework.data.redis.core.StringRedisTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java | 18 | private final StringRedisTemplate redis; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java | 22 | public RedisOfferingCacheAdapter(StringRedisTemplate redis, |

## Messaging / RabbitMQ — 23 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 22 | import org.springframework.amqp.core.AmqpAdmin; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 125 | private AmqpAdmin amqpAdmin; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java | 11 | import org.springframework.amqp.core.AmqpAdmin; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java | 14 | import org.springframework.amqp.rabbit.core.RabbitTemplate; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java | 77 | RabbitTemplate rabbitTemplate; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java | 80 | AmqpAdmin amqpAdmin; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java | 9 | import org.springframework.amqp.rabbit.core.RabbitTemplate; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java | 26 | RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class); |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java | 43 | RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class); |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisherTest.java | 58 | RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class); |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java | 24 | @RabbitListener(bindings = @QueueBinding( |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java | 25 | value = @Queue(value = "booking.payment.approved.queue", durable = "true"), |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java | 26 | exchange = @Exchange(value = "payment.events", type = "topic"), |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java | 38 | @RabbitListener(bindings = @QueueBinding( |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java | 39 | value = @Queue(value = "booking.payment.rejected.queue", durable = "true"), |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/PaymentResultConsumer.java | 40 | exchange = @Exchange(value = "payment.events", type = "topic"), |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumer.java | 24 | @RabbitListener(queues = RabbitConfiguration.NOTIFICATION_QUEUE) |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java | 6 | import org.springframework.amqp.rabbit.core.RabbitTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java | 12 | private final RabbitTemplate rabbitTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java | 17 | public RabbitPaymentEventPublisher(RabbitTemplate rabbitTemplate) { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java | 11 | import org.springframework.amqp.rabbit.core.RabbitTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java | 19 | private final RabbitTemplate rabbitTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java | 21 | public RabbitNotificationPublisher(RabbitTemplate rabbitTemplate) { |

## Messaging / Kafka — 6 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java | 17 | @KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaAuditConsumer.java | 14 | @KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java | 8 | import org.springframework.kafka.core.KafkaTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java | 17 | private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java | 8 | import org.springframework.kafka.core.KafkaTemplate; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java | 17 | private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate; |

## Programmatic initializers / seeders — 2 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java | 30 | public class AuditMongoInitializer implements ApplicationRunner { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AdminSeeder.java | 13 | public class AdminSeeder implements CommandLineRunner { |

## Security markers — 56 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingControllerSecurityTest.java | 14 | import com.riwi.skillbridge.infrastructure.security.JwtService; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingControllerSecurityTest.java | 59 | private JwtService jwtService; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingSecurityTest.java | 80 | JwtService jwtService; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserControllerTest.java | 14 | import com.riwi.skillbridge.infrastructure.security.JwtService; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserControllerTest.java | 65 | @MockitoBean JwtService jwtService; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 16 | import com.riwi.skillbridge.infrastructure.security.JwtService; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 17 | import io.jsonwebtoken.Jwts; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 30 | import org.springframework.security.crypto.password.PasswordEncoder; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 115 | private JwtService jwtService; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 117 | private PasswordEncoder passwordEncoder; |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java | 318 | return Jwts.builder() |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilterTest.java | 17 | class JwtAuthenticationFilterTest { |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilterTest.java | 21 | private final JwtService jwtService = mock(JwtService.class); |
| backend/src/test/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilterTest.java | 24 | private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService, cookieService); |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 4 | import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 16 | import org.springframework.security.crypto.password.PasswordEncoder; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 20 | import org.springframework.security.web.SecurityFilterChain; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 37 | @EnableMethodSecurity |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 46 | SecurityFilterChain security(HttpSecurity http, |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 47 | JwtAuthenticationFilter jwtFilter, |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 82 | .authorizeHttpRequests(auth -> auth |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 83 | .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout", |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 87 | .requestMatchers(HttpMethod.GET, "/api/offerings").permitAll() |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 88 | .requestMatchers("/actuator/prometheus").permitAll() |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 89 | .requestMatchers("/error").permitAll() |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java | 97 | PasswordEncoder passwordEncoder() { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 54 | @PreAuthorize("hasRole('PROVIDER')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 66 | @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 80 | @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 97 | @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java | 110 | @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminOfferingController.java | 24 | @PreAuthorize("hasRole('ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 46 | @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 53 | @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java | 61 | @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java | 34 | @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java | 43 | @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java | 27 | @PreAuthorize("hasRole('ADMIN')") |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 8 | import com.riwi.skillbridge.infrastructure.security.JwtService; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 25 | private final JwtService jwtService; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java | 28 | public AuthController(AuthUseCase auth, JwtService jwtService, AuthCookieService cookies) { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/SpringPasswordHasherAdapter.java | 4 | import org.springframework.security.crypto.password.PasswordEncoder; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/SpringPasswordHasherAdapter.java | 9 | private final PasswordEncoder encoder; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/SpringPasswordHasherAdapter.java | 10 | public SpringPasswordHasherAdapter(PasswordEncoder encoder) { this.encoder = encoder; } |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java | 5 | import io.jsonwebtoken.Jwts; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java | 18 | public class JwtService implements TokenPort { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java | 22 | public JwtService(@Value("${app.jwt.secret}") String secret, |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java | 31 | return Jwts.builder() |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java | 51 | return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java | 11 | import org.springframework.web.filter.OncePerRequestFilter; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java | 16 | public class JwtAuthenticationFilter extends OncePerRequestFilter { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java | 17 | private final JwtService jwtService; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java | 21 | public JwtAuthenticationFilter(JwtService jwtService, |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/CsrfCookieFilter.java | 8 | import org.springframework.web.filter.OncePerRequestFilter; |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/CsrfCookieFilter.java | 12 | public class CsrfCookieFilter extends OncePerRequestFilter { |
| backend/src/main/java/com/riwi/skillbridge/infrastructure/security/DatabaseUserDetailsService.java | 9 | public class DatabaseUserDetailsService implements UserDetailsService { |

## Advertencia

La búsqueda es textual: puede omitir anotaciones multilínea o producir falsos positivos. Usa los resultados como índice de navegación.
