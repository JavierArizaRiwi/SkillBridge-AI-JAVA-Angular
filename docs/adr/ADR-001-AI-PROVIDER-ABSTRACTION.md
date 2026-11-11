# ADR-001: AI Provider Abstraction

**Status**: Accepted — **partially superseded by [ADR-0007](0007-ai-provider-fallback-chain.md)** (selection of a single adapter by property) · see [Errata 2026-11-09](#errata-2026-11-09)  
**Date**: 2026-10-07  
**Deciders**: SkillBridge Development Team  
**Affected Version**: 0.0.1 (HU-15)

---

## Context

The SkillBridge recommendation engine initially used only Google Gemini as the AI provider for generating service recommendations. This created a hard dependency on a single vendor, limiting flexibility and preventing comparison of AI providers without significant code changes.

### Problem Statement

1. **Vendor Lock-in**: Changing providers required recompilation and redeployment
2. **No Flexibility**: Production-time provider switching was impossible
3. **Limited Evaluation**: Difficult to benchmark providers side-by-side
4. **Risk**: Single point of failure if one provider degraded

### Requirements

- Enable runtime switching between AI providers without recompilation
- Maintain backward compatibility with existing Gemini integration
- Support multiple providers simultaneously (Gemini, OpenAI, future: Claude, Cohere)
- Allow A/B testing and gradual provider migration
- Minimize code changes to existing business logic

---

## Decision

We implement **provider abstraction using Spring AI's native adapters** and conditional bean activation via properties.

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│     Application Layer (UseCase/Service)                 │
│        Uses: AiRecommendationPort (interface)          │
└────────────────┬────────────────────────────────────────┘
                 │
    ┌────────────┴────────────┐
    │                         │
┌───▼──────────────────┐  ┌──▼──────────────────┐
│ GeminiAiAdapter      │  │ OpenAiAiAdapter     │
│ (Conditional)        │  │ (Conditional)       │
│ @ConditionalOnProp   │  │ @ConditionalOnProp  │
│ provider=gemini      │  │ provider=openai     │
└─────────┬────────────┘  └────────┬────────────┘
          │                        │
    ┌─────▼────────────────────────▼─────┐
    │   Spring AI ChatClient (Unified)    │
    │   Abstracts Gemini & OpenAI APIs    │
    └─────┬────────────────────────────────┘
          │
    ┌─────▼──────────────────────┬───────────────┐
    │                            │               │
┌───▼──────────┐  ┌─────────────▼────┐  ┌──────▼─────┐
│ Gemini API   │  │  OpenAI API      │  │ Future:    │
│ studio.google│  │  api.openai.com  │  │ Claude...  │
└──────────────┘  └──────────────────┘  └────────────┘
```

### Implementation Details

#### 1. **Adapter Pattern**
- Each AI provider gets its own adapter class implementing `AiRecommendationPort`
- Adapters handle provider-specific configuration and error mapping
- No application code knows about specific providers

#### 2. **Spring Conditional Activation**
```java
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiAiAdapter implements AiRecommendationPort { ... }

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiAiAdapter implements AiRecommendationPort { ... }
```

**Rationale**: 
- Spring automatically instantiates only the active adapter
- No service locator or factory pattern needed
- Zero runtime overhead
- Type-safe dependency injection

#### 3. **Provider Configuration**
Properties managed via `AiProviderProperties`:
```properties
# application.yml or .env
app.ai.provider: gemini|openai
app.ai.timeout-seconds: 10
```

**Advantages**:
- Environment-specific configuration
- No code changes needed to switch providers
- Docker/Kubernetes friendly
- CI/CD integration-ready

#### 4. **Unified Error Handling**
All adapters map provider-specific exceptions to domain exceptions:
```java
IllegalArgumentException → AiProviderException(INVALID_INPUT)  // API key issues
RuntimeException → AiProviderException(UNAVAILABLE)             // Network/timeout
AiProviderException → AiProviderException (re-throw as-is)      // Domain errors
```

#### 5. **Spring AI Integration**
Both adapters use Spring AI's `ChatClient` abstraction:
- Same method chain: `chatClient.prompt().user(...).call().content()`
- Provider configuration handled by Spring (auto-detection via classpath JARs)
- Dependency injection: `ChatClient.Builder` injected by Spring

---

## Alternatives Considered

### Alternative 1: Enum-based Provider Selection
```java
enum AiProvider { GEMINI, OPENAI }
@Service
class AiRecommendationService {
    private final AiProvider provider;
    // if/switch logic to select adapter
}
```
**Rejected because**:
- Introduces runtime if/switch logic
- Requires recompilation to add new providers
- Service becomes a god object
- Less testable (conditional logic in business layer)

### Alternative 2: Service Locator Pattern
```java
@Service
class AiAdapterLocator {
    public AiRecommendationPort getAdapter(String name) { ... }
}
```
**Rejected because**:
- Anti-pattern (hides dependencies)
- Type-unsafe
- Hard to test and trace
- Poor IDE support (no "find usages")

### Alternative 3: Abstract Factory with Reflection
```java
class AiAdapterFactory {
    public static AiRecommendationPort create(String provider) {
        return (AiRecommendationPort) 
            Class.forName("...adapters." + provider + "Adapter")
                 .newInstance();
    }
}
```
**Rejected because**:
- Reflection overhead
- Harder to debug
- Requires classpath scanning
- Spring already handles this better with conditionals

### Alternative 4: Single Adapter with Provider Switching
```java
@Service
class AiRecommendationService implements AiRecommendationPort {
    private final ChatClient geminaiClient, openaiClient;
    public String recommend(...) {
        return switch(provider) {
            case GEMINI -> geminaiClient...
            case OPENAI -> openaiClient...
        }
    }
}
```
**Rejected because**:
- Loads all provider clients even if not used
- Business logic polluted with provider selection
- Harder to test (always mocking both)
- Violates Single Responsibility Principle

---

## Consequences

### Positive

✅ **Runtime Provider Switching**: No recompilation needed  
✅ **Easy Provider Addition**: Just add new adapter + property value  
✅ **Type Safety**: Full IDE support, compile-time verification  
✅ **Testability**: Each adapter tested in isolation  
✅ **A/B Testing**: Can route different requests to different providers  
✅ **Gradual Migration**: Old code works with new providers  
✅ **Spring-native**: Leverages Spring Boot conventions  
✅ **Performance**: Zero runtime overhead when provider is selected  

### Negative

⚠️ **Classpath Dependency**: Must have either Gemini or OpenAI JAR on classpath  
⚠️ **Configuration Required**: Must set `app.ai.provider` property  
⚠️ **Multiple Implementations**: Maintaining multiple adapters requires discipline  
⚠️ **Test Coverage**: Each adapter needs its own test suite  

### Mitigation

- Default provider in application.yml prevents deployment failures
- CI/CD validates that exactly one provider is enabled
- Adapters use shared error handling to reduce duplicate code
- Comprehensive test coverage ensures consistency

---

## Implementation Timeline

**Phase 1** ✅ (Completed)
- Create `AiRecommendationPort` interface
- Implement `GeminiAiAdapter`
- Test with Gemini only

**Phase 2** ✅ (Completed)
- Add Spring AI OpenAI dependency to pom.xml
- Implement `OpenAiAiAdapter`
- Parallel test coverage for both adapters
- Comparison analysis (latency, cost, quality)

**Phase 3** (In Progress)
- Create ADR (this document)
- Update README with provider information
- Create migration guide

**Phase 4** (Future)
- Enable A/B testing framework
- Add metrics/monitoring per provider
- Support additional providers (Claude, Cohere, Llama)

---

## Supporting Evidence

### Latency Comparison (2026-10-07)
- **Gemini**: 1350ms median (range: 1200-1500ms)
- **OpenAI**: 1050ms median (range: 950-1200ms)
- **Winner**: OpenAI (22% faster)

### Cost Comparison (Annual, 10K requests/day)
- **Gemini**: $126/year
- **OpenAI (4-turbo)**: $14,040/year
- **OpenAI (3.5-turbo)**: $702/year
- **Recommendation**: Use Gemini for dev, 3.5-turbo for production

### Quality Comparison
- **Gemini**: 8.4/10 (good, concise)
- **OpenAI (4-turbo)**: 9.2/10 (excellent, detailed)
- **Winner**: OpenAI (8% quality improvement)

See `docs/adr/AI_PROVIDER_COMPARISON.md` for full analysis.

---

## Related Documents

- `docs/adr/AI_PROVIDER_COMPARISON.md` — Provider evaluation and comparison
- `docs/ISSUES_AND_BLOCKERS.md` — Outstanding blockers from Phase 3
- Backend code: `infrastructure/adapter/out/ai/*.java`

---

## Validation Criteria

✅ Both adapters pass their unit tests (8 tests each)  
✅ No changes required to application business logic  
✅ Runtime provider switching works (tested manually)  
✅ Error handling is consistent across providers  
✅ Spring conditional activation works correctly  
✅ Zero performance degradation vs original Gemini-only implementation  

---

## Sign-Off

**Accepted by**: SkillBridge Development Team  
**Date**: 2026-10-07  
**Version**: 1.0  
**Next Review**: 2026-11-07 (post-Phase 4 completion)


---

## Errata 2026-11-09

*Anotación añadida al revisar el ADR contra el código. El cuerpo anterior se conserva tal como se aprobó; esta sección lo corrige donde ya no es cierto. Detalle y evidencia en [`docs/refinement/adr-001-review.md`](../refinement/adr-001-review.md). Marcas: `[VERIFIED]` comprobado en el código.*

### Lo que sigue vigente

Un puerto (`AiRecommendationPort`) con un adaptador por proveedor sobre el `ChatClient` de Spring AI, que aísla al dominio del proveedor.

### Correcciones

| # | El ADR dice | Realidad `[VERIFIED]` |
|---|---|---|
| 1 | `app.ai.timeout-seconds: 10` protege las llamadas | La propiedad **no se aplica** en `GeminiAiAdapter` ni `OpenAiAiAdapter`; solo figura en la configuración y como texto en un mensaje de error de OpenAI. Solo `ElevenLabsTextToSpeechAdapter` aplica timeouts reales. |
| 2 | "A/B testing: enrutar peticiones distintas a proveedores distintos" | Con `@ConditionalOnProperty` **solo hay un bean activo**: no hay enrutado por petición. |
| 3 | "Cambio en tiempo de ejecución" | El cambio ocurre **al arrancar**; requiere reiniciar. |
| 4 | Mapeo de errores: `IllegalArgumentException → INVALID_INPUT`, `RuntimeException → UNAVAILABLE` | Gemini clasifica hoy **por texto del mensaje** (`TIMEOUT`, `RATE_LIMITED`, `INVALID_INPUT`, `UNAVAILABLE`, `INTERNAL_ERROR`); OpenAI sigue colapsando todo a `UNAVAILABLE`. No son consistentes entre sí. |
| 5 | "CI/CD valida que exactamente un proveedor esté habilitado" | No existe tal validación. |
| 6 | "8 pruebas por adaptador" | Gemini tiene 13; OpenAI, 8. |
| 7 | Alcance: recomendaciones | Hoy existen además `VisionPort`, `SpeechToTextPort` (Gemini/Groq, `app.ai.audio-provider`) y `TextToSpeechPort` (ElevenLabs/NoOp). Este ADR no los cubre. |
| 8 | Fase 4 (A/B y métricas por proveedor) | No hay implementación en el código, y el A/B contradice la arquitectura (corrección 2). |

### Sobre la evidencia

La comparación de [`AI_PROVIDER_COMPARISON.md`](AI_PROVIDER_COMPARISON.md) (latencia, calidad y coste) es **orientativa**: se basa en 10 peticiones por proveedor, un único prompt y puntuaciones de calidad subjetivas, y no describe prueba alguna para `gpt-3.5-turbo`. **No debe citarse como prueba** de una recomendación de proveedor o modelo. Tampoco coinciden entre sí: el comparativo recomienda GPT-4 Turbo, este ADR recomienda `gpt-3.5-turbo` en producción y la configuración usa Gemini por defecto.

### Referencia corregida

En *Related Documents*, `docs/ISSUES_AND_BLOCKERS.md` nunca existió con ese nombre; el documento correspondiente es [`docs/history/HU-15-ISSUES_AND_BLOCKERS.md`](../history/HU-15-ISSUES_AND_BLOCKERS.md).

### Sustitución

La selección de **un único adaptador activo por propiedad** (§Decision.2 y 3) queda sustituida por una cadena de fallback con adaptador compuesto: ver [ADR-0007](0007-ai-provider-fallback-chain.md). Siguen vigentes los puertos, el aislamiento del dominio y el uso de Spring AI.

### Numeración

Este documento conserva el identificador `ADR-001` por estar referenciado en `CLAUDE.md`, `INTEGRATIONS.md` y las specs. No debe confundirse con [`0001-modular-monolith-first.md`](0001-modular-monolith-first.md). Ver el [índice](README.md).
