# HU-15: Issues and Blockers Log

## Overview

This document tracks all bugs, blockers, and issues encountered during the implementation of HU-15 (AI Provider Migration).

**Last Updated**: 2026-10-07  
**Status**: Phase 4 — Provider Evaluation

---

## Active Blockers

### None at this moment

---

## Resolved Issues

### None at this moment

---

## Completed Phases

### Phase 1: Repository Analysis ✅
- Analyzed project structure
- Identified Spring Boot 3.5.6 with Java 21
- Found existing GeminiAiAdapter implementation
- Documented architecture as Hexagonal

### Phase 2: Architecture Explanation ✅
- Explained Dependency Inversion Principle
- Documented adapter pattern
- Explained port concept
- Identified need for provider abstraction

### Phase 3: Baseline Documentation ✅
- Documented Gemini baseline metrics
- Created configuration infrastructure (AiProviderProperties)
- Implemented specific exception types (AiProviderException)
- Created MockAiAdapter for testing
- Added unit tests for GeminiAiAdapter (8 tests, all passing)

**Commits in Phase 3:**
1. docs: documentar baseline del proveedor de ía actual (Gemini)
2. config: crear configuración centralizada del proveedor de IA
3. feat: mejorar manejo de errores del adaptador de IA con excepciones específicas
4. docs: crear registro de issues y blockers para HU-15
5. test: crear adaptador mock para pruebas sin conexión externa
6. merge: resolver conflicto en application.yml - mantener ambas secciones (ai + booking)
7. test: agregar tests unitarios del adaptador GeminiAiAdapter

---

## Phase 4: Provider Evaluation — CURRENT

### Evaluation Criteria

**For each candidate provider, measure:**

1. **Latency** (milliseconds)
   - Cold start latency
   - Warm latency (after first call)
   - Median latency
   - P95/P99 latency if possible

2. **Cost** (per 1M tokens)
   - Input token price
   - Output token price
   - Free tier availability and limits

3. **Quality**
   - Relevance of recommendations
   - Accuracy of catalog matching
   - Response formatting
   - Hallucination tendency

4. **Integration**
   - Spring AI compatibility
   - Setup complexity
   - Documentation quality
   - Error handling

### Candidate Providers to Evaluate

- [ ] OpenAI (GPT-4 Turbo / GPT-3.5 Turbo)
- [ ] Anthropic Claude (Claude 3 Opus / Sonnet)
- [ ] Cohere (Command R / Command R+)
- [ ] Mistral AI

### Recommended Evaluation Approach

1. **Set up test harness**
   - Create OpenAiAiAdapter following GeminiAiAdapter pattern
   - Create ClaudeAiAdapter for second provider
   - Use same MockAiAdapter for testing

2. **Measure latency**
   - Run 10+ calls for each provider
   - Record cold/warm times
   - Calculate median + P95

3. **Document cost**
   - Calculate per-request cost
   - Compare pricing structures
   - Consider free tier limits

4. **Test quality**
   - Use same test prompts as Gemini baseline
   - Manual review of recommendations
   - Test edge cases

5. **Create comparison document**
   - AI_PROVIDER_COMPARISON.md
   - Include pricing table
   - Include latency comparison
   - Include quality assessment

---

## Known Limitations (Not blockers, but important context)

### 1. Testcontainers Requires Docker
**Severity**: Low  
**Affected Component**: Backend tests  
**Description**: 
- `JpaOfferingRepositoryTest` requires Docker to be running (uses Testcontainers for PostgreSQL)
- This is expected and correct behavior for integration tests
- Unit tests (Mockito-based) run without Docker

**Impact**: 
- Cannot run full test suite (`mvn test`) in environments without Docker
- Workaround: Use `mvn clean compile` to verify compilation, or run tests individually

**Resolution**: No action needed. This is by design. Integration tests should run in CI pipeline with Docker.

### 2. Spring AI Version Compatibility
**Severity**: Low  
**Affected Component**: Adapter implementations  
**Description**: 
- Spring AI 1.1.8 includes specific ChatClient API
- New providers need to follow same pattern
- Different providers might have different SDK versions

**Impact**: Need to verify SDK compatibility for each provider

**Resolution**: Check provider SDK supports Spring AI integration before implementing

---

## Testing Strategy

### Current Test Status (2026-10-07)

| Test | Status | Notes |
|---|---|---|
| `BookingServiceTest` | ✅ Pass | Unit test with Mockito, no external dependencies |
| `OfferingServiceTest` | ✅ Pass | Unit test with Mockito, no external dependencies |
| `GeminiAiAdapterTest` | ✅ Pass (8/8) | Unit tests with Mockito, mocks ChatClient |
| `JpaOfferingRepositoryTest` | ⚠️ Skip in local | Requires Docker/Testcontainers |

### Unit Tests Without External Dependencies
- ✅ All unit tests pass
- ✅ Compilation successful with `-DskipTests`

---

## Items for Next Phases

### Phase 4 Deliverables
- [ ] OpenAiAiAdapter implementation
- [ ] OpenAI baseline metrics
- [ ] Comparison document: Gemini vs OpenAI
- [ ] Tests for OpenAI adapter
- [ ] ADR (Architecture Decision Record)

### Phase 5 Deliverables (Optional)
- [ ] Additional provider adapters (Claude, Cohere, etc.)
- [ ] Provider selection/switching mechanism
- [ ] Provider health checks
- [ ] Fallback strategies

---

## Checklist: Before Phase 4 Completion

- [x] Compile successful
- [x] All unit tests pass (8/8)
- [x] Configuration infrastructure in place
- [x] Error handling improved
- [x] Mock adapter created
- [x] Baseline documentation complete
- [ ] Phase 4: Evaluate and implement candidate provider
- [ ] Comparison document created
- [ ] ADR created
- [ ] PR created and reviewed

---

## Notes for Implementation Team

### Important Reminders

1. **No API keys in code**: All API keys must be environment variables
2. **Timeout configuration**: Currently set to 10 seconds in `AiProviderProperties`
3. **Error types**: Use `AiProviderException.ErrorType` enum for consistent error categorization
4. **Mock for tests**: Always use `MockAiAdapter` in tests, never call real APIs
5. **Documentation**: Keep this file updated as issues are discovered
6. **Adapter pattern**: Each new provider = new adapter implementing `AiRecommendationPort`

### Testing Pattern for New Adapters

```java
// Good: Using mock
@MockBean
private AiRecommendationPort aiPort;

when(aiPort.recommend(anyString(), anyList()))
    .thenReturn("Mock recommendation");

// Bad: Calling real API in tests
var adapter = new OpenAiAiAdapter(realApiKey);
```

---

## Version History

| Date | Status | Changes |
|---|---|---|
| 2026-10-07 | Phase 4 | GeminiAiAdapter tests passing. Ready for Phase 4 evaluation. |
| 2026-10-06 | Phase 3 | Initial log created before Phase 3 completion |

---

**Next Phase**: Phase 4 — Evaluate candidate AI provider (OpenAI)  
**Target Date**: End of session 2026-10-07
