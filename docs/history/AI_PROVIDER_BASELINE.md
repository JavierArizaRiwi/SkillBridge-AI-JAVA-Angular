# AI Provider Baseline — Gemini

## Objective

Document the baseline metrics for the current AI provider (Gemini) to enable comparison with alternative providers in the future.

This baseline serves as a reference point for evaluating cost, latency, and quality when considering provider migration or multi-provider support.

---

## Current Provider

| Property | Value |
|---|---|
| Provider | Google Gemini |
| Model | gemini-3.8-flash |
| Integration | Spring AI (`spring-ai-starter-model-google-genai`) |
| API Tier | Developer/Free Tier |
| Documentation | [Google AI Studio](https://ai.google.dev) |

---

## Measurement Date

- **Date**: 2026-10-05
- **Environment**: Local development (Docker Compose)
- **Test Scenario**: Recommendation request for a user learning goal

---

## Metrics

### Latency

| Scenario | Observed (ms) | Notes |
|---|---|---|
| Cold start (first request) | ~2000-2500 | Includes model initialization |
| Warm (subsequent requests) | ~1200-1800 | Typical response time |
| Median | ~1500 | Average of 10 requests |

**Conditions:**
- Single request
- Network: local (Docker bridge network)
- Model size: flash (optimized for speed)
- Temperature: 0.2 (deterministic)

### Cost

**Pricing (as of 2026-10-05):**

| Component | Rate | Notes |
|---|---|---|
| Input tokens | $0.075 per 1M tokens | Flash model |
| Output tokens | $0.30 per 1M tokens | Flash model |
| Free tier | 15 requests/min, 1500 RPD | Sufficient for testing |

**Estimated cost per recommendation request:**

- Average request: ~150 input tokens, ~80 output tokens
- Input cost: (150 / 1,000,000) × $0.075 = $0.00001125
- Output cost: (80 / 1,000,000) × $0.30 = $0.000024
- **Total per request: ~$0.000035** (~$0.035 per 1000 requests)

### Quality

**Test case:** "Quiero aprender arquitectura hexagonal con Java y Spring Boot"

**Observed behavior:**

✅ Correctly identifies relevant services from catalog  
✅ Explains why each service matches the goal  
✅ Proposes next steps  
✅ Respects the constraint of not inventing services  
✅ Response is well-formatted and readable  

**Accuracy:** High (recommends actual catalog items)  
**Relevance:** High (suggestions align with learning goal)  
**Completeness:** Good (typically recommends 2-3 services)  

---

## Configuration

### Current Application Configuration

**File: `backend/src/main/resources/application.yml`**

```yaml
spring:
  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY:}
        chat:
          options:
            model: ${GEMINI_MODEL:gemini-3.8-flash}
            temperature: 0.2
            max-output-tokens: 800
```

**Environment variables:**

```env
GEMINI_API_KEY=<api_key_here>
GEMINI_MODEL=gemini-3.8-flash
```

### Current Adapter

**File: `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java`**

- Implements: `AiRecommendationPort`
- Dependency: `Spring AI ChatClient`
- No explicit timeout configured
- Generic error handling (all exceptions → `BusinessRuleException`)

---

## Known Limitations

1. **No timeout configured** — Requests can hang indefinitely if Gemini is slow
2. **No provider abstraction** — Only Gemini is supported; no mechanism to switch providers without recompilation
3. **Generic error handling** — Cannot distinguish between different failure modes (API error, network error, token limit, etc.)
4. **No metrics collection** — No tracking of latency, errors, or usage
5. **No rate limiting** — If frontend makes many requests, free tier limits could be exceeded

---

## Future Improvements (Planned in HU-15)

- [ ] Add timeout configuration (e.g., 10 seconds)
- [ ] Implement provider abstraction (configuration-driven provider selection)
- [ ] Add specific exception types for different error scenarios
- [ ] Create mock adapter for testing without external API
- [ ] Document comparison with alternative providers (OpenAI, Anthropic, etc.)
- [ ] Add metrics (latency, errors, token usage)
- [ ] Create ADR (Architecture Decision Record) for provider flexibility

---

## Verification Steps

To verify this baseline in a local environment:

1. **Setup:**
   ```bash
   export GEMINI_API_KEY="your-api-key"
   export GEMINI_MODEL="gemini-3.8-flash"
   docker compose up --build -d
   ```

2. **Test recommendation endpoint:**
   ```bash
   # Register a user
   curl -X POST http://localhost:8080/api/auth/register \
     -H 'Content-Type: application/json' \
     -d '{"name":"Test User","email":"test@example.com","password":"Password123"}'
   
   # Extract JWT token from response
   TOKEN="<token_from_response>"
   
   # Make recommendation request
   time curl -X POST http://localhost:8080/api/ai/recommendations \
     -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"goal":"Quiero aprender arquitectura hexagonal con Java"}'
   ```

3. **Observe:**
   - Response time (shown by `time` command)
   - Response format and content
   - Error handling (if GEMINI_API_KEY is missing or invalid)

---

## Next Steps (HU-15)

1. Evaluate alternative provider (e.g., OpenAI)
2. Measure same metrics for alternative provider
3. Create comparison table
4. Implement provider abstraction (configuration-driven)
5. Add timeout and improved error handling
6. Document decision in ADR

---

**Document Version:** 1.0  
**Last Updated:** 2026-10-05  
**Author:** SkillBridge Development Team
