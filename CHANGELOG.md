# Changelog

Todos los cambios notables en este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added - HU-22: Demand Analytics (Análisis Predictivo de Demanda)
- **DemandAnalyticsService**: Motor de análisis con fórmulas predictivas
  - `conversionRate()`: Calcula tasa de conversión (efectiveBookings / recommendations)
  - `growthRate()`: Calcula crecimiento entre períodos (recent - previous) / previous
  - `trendScore()`: Score compuesto 0-100 (50% conversión + 30% crecimiento + 20% volumen)
  - `forecastNext7Days()`: Predicción de bookings con amplificador de crecimiento
- **RecommendationDailyMetric**: Modelo para agregación diaria de métricas
- **KafkaAnalyticsConsumer**: Consumidor de eventos de recomendación en tiempo real
- **RecommendationAnalyticsRepositoryPort**: Interfaz para persistencia de analytics
- **GetTrendingServicesUseCase**: Caso de uso para obtener servicios trending
- **TrendingServiceResponse**: DTO con datos de trending (offeringId, name, trendScore, forecast)
- **Frontend trending.component.ts**: Componente Angular con visualización de trending services
- **Frontend trending.service.ts**: Servicio de comunicación con backend
- **Database Migrations**:
  - `V6__recommendation_daily_metrics.sql`: Tabla para métricas diarias
- **Tests**:
  - `DemandAnalyticsServiceTest`: 25+ casos unitarios con cobertura completa de fórmulas
  - `KafkaAnalyticsConsumerTest`: Tests de consumidor Kafka

### Added - HU-15: AI Provider Migration (Migración de Proveedores de IA)
- **Abstracción de Proveedores de IA**:
  - Support para Gemini y OpenAI
  - Soporte para múltiples modelos de IA con fallback
- **Error Handling Robusto**:
  - `AiProviderException` con tipos de error: INVALID_INPUT, UNAVAILABLE, INTERNAL_ERROR
  - Mapeo automático de excepciones con mensajes informativos
  - Inclusión de timeouts en mensajes de error
- **Tests Reparados**:
  - `GeminiAiAdapterTest`: 8 tests validando manejo de errores
  - `OpenAiAiAdapterTest`: 8 tests validando manejo de errores

### Added - HU-23: Recomendaciones Multimodales con IA
- **Audio Transcription**:
  - Integración de Groq para transcripción ultra-rápida (Whisper Large V3 Turbo)
- **Structured Output**:
  - Recomendaciones con salida estructurada en lugar de arrays mockados
  - Fallback rules estrictas para validación
- **UI Improvements**:
  - Modal para respuestas de IA
  - Reemplazo de emojis con iconos SVG
  - Redirección automática a booking después de recomendación
- **Environment Variables**:
  - GROQ_API_KEY para integración de Groq
  - AI_AUDIO_PROVIDER para seleccionar proveedor de audio

### Added - HU-14: Simulación de Pasarela de Pago
- **Payment Simulation**:
  - Simulación completa del flujo de pago
  - Contador de reservas canceladas con filtro interactivo
- **Database Migrations**:
  - `V4__booking_cancellation_audit.sql`: Auditoría de cancelaciones
- **UI Enhancements**:
  - Contador visual de cancelaciones
  - Filtros interactivos para reservas

### Changed
- **Modelo Booking**:
  - Agregado campo `version` (long) para versionado optimista
  - Actualización de constructores en tests
- **BookingCancelledPayload**:
  - Agregado `offeringId` para analytics
- **Database Migrations**:
  - `V5__recommendation_events.sql`: Tabla para eventos de recomendación
- **Environment Configuration**:
  - Soporte para GROQ_API_KEY y AI_AUDIO_PROVIDER
  - Mapeo correcto de variables de entorno para evitar agotamiento de cuota

### Fixed
- Compilación de tests de IA adapters con nueva firma de Offering
- Error en AiRecommendationService al convertir offeringId a UUID
- Encoding de caracteres en UI (typos por reemplazo incorrecto)
- Manejo de excepciones en integración Gemini y OpenAI con timeouts
- Validación de archivos de medios en navegadores móviles

---

## Notas Técnicas

### Requisitos
- **Java**: 21+
- **Node**: 22+
- **Database**: PostgreSQL 14+
- **Message Queue**: Apache Kafka
- **Docker**: Para tests de integración (TestContainers)

### Migraciones BD Requeridas
Se ejecutarán automáticamente con Flyway:
- V4: Auditoría de cancelaciones
- V5: Eventos de recomendación
- V6: Métricas diarias de analytics

### Variables de Entorno Requeridas
```
GEMINI_API_KEY=<tu-api-key>
OPENAI_API_KEY=<tu-api-key>
GROQ_API_KEY=<tu-api-key>
AI_AUDIO_PROVIDER=groq|other
```

### Cambios en la Arquitectura
- Adición de Kafka para procesamiento asincrónico de analytics
- Nueva capa de persistencia para RecommendationAnalyticsRepositoryPort
- Abstracción de proveedores de IA mejorada

### Testing
- **Backend**: 104 tests unitarios pasando
- **Frontend**: Tests con ChromeHeadless
- **Total**: 60+ archivos modificados, 3,120+ líneas de código

---

## Estadísticas

| Métrica | Valor |
|---------|-------|
| Archivos modificados/creados | 60+ |
| Líneas de código agregadas | 3,120+ |
| Tests agregados | 25+ (Analytics) + 8 (AI Adapters) |
| Migraciones BD | 3 (V4, V5, V6) |
| Nuevas clases backend | 15+ |
| Nuevos componentes frontend | 2 |
| Commits integrados | 8a3133c (HU-23) + feature/HU-22 |

---

## Breaking Changes

⚠️ **Ninguno documentado** - Las integraciones son aditivas y retrocompatibles.

---

## Deployment Notes

### Para Producción
1. Ejecutar migraciones BD (V4, V5, V6)
2. Configurar variables de entorno (GEMINI_API_KEY, OPENAI_API_KEY, GROQ_API_KEY)
3. Iniciar Kafka para procesamiento de analytics
4. Ejecutar `mvn clean verify` para validar compilación
5. Deploy de imágenes Docker buildadas en CI/CD

### Monitoreo
- Monitores de Kafka topics (recommendation-analytics-events, etc)
- Logs de errores en AiRecommendationService
- Métricas de trending en RecommendationDailyMetricRepository

---

**Última actualización**: October 2026
**Preparado por**: Kiro AI
