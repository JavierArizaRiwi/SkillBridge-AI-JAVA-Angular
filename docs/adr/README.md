# Decisiones de arquitectura (ADR)

Registro de las decisiones técnicas relevantes. Un ADR **no se reescribe** cuando deja de ser cierto: se anota con una errata o lo sustituye otro ADR.

## Índice

| ID | Título | Estado | Notas |
|---|---|---|---|
| [0001](0001-modular-monolith-first.md) | Empezar como monolito modular | Accepted | |
| [0002](0002-managed-services-in-cloud.md) | Dependencias gestionadas en la topología cloud gratuita | Accepted (entorno académico) | |
| [0003](0003-kafka-event-topic-unification.md) | Unificación de topics de Kafka (dominio y auditoría) | Accepted (de facto en el código) | |
| [0004](0004-spec-driven-development.md) | Adopción de Spec-Driven Development (GitHub Spec Kit) | Accepted | |
| [0005](0005-proportionate-hybrid-sdd.md) | SDD híbrido proporcional | Accepted | |
| [0006](0006-hybrid-monolith-and-services.md) | Arquitectura híbrida: monolito + servicios satélite | **Proposed** | Contratos en [`../contracts/`](../contracts/) |
| [0007](0007-ai-provider-fallback-chain.md) | Cadena de fallback de proveedores de IA | **Proposed** | Sustituye parcialmente a ADR-001 · HU-27/28 |
| [ADR-001](ADR-001-AI-PROVIDER-ABSTRACTION.md) | Abstracción de proveedores de IA | Accepted, **parcialmente sustituido por 0007** | Con [errata 2026-11-09](ADR-001-AI-PROVIDER-ABSTRACTION.md#errata-2026-11-09) |

## Aviso sobre la numeración

Hay dos identificadores parecidos que **no son lo mismo**:

- **`0001`** es *monolito modular primero*.
- **`ADR-001`** es *abstracción de proveedores de IA*. Es un nombre heredado de la HU-15 que se conserva porque `CLAUDE.md`, `INTEGRATIONS.md` y las specs ya lo referencian.

Los ADR nuevos usan el formato `NNNN-titulo-en-minusculas.md`. El siguiente libre es **0008**.

## Material de apoyo (no son ADR)

- [`AI_PROVIDER_COMPARISON.md`](AI_PROVIDER_COMPARISON.md): comparativa de Gemini y OpenAI de octubre de 2026. **Orientativa**; ver la errata de ADR-001 sobre su método.
- [`../refinement/adr-001-review.md`](../refinement/adr-001-review.md): revisión de ADR-001 contra el código.

## Cómo añadir un ADR

1. Copia la estructura de un ADR reciente: *Status*, *Date*, *Context*, *Decision*, *Alternatives*, *Consequences*.
2. Estado inicial `Proposed`; pasa a `Accepted` cuando lo apruebe una persona.
3. Si cambia una decisión anterior, no edites su cuerpo: añade una errata y enlaza el ADR sucesor.
4. Cualquier cambio de límites de dominio, puertos o topics de mensajería requiere ADR (`CLAUDE.md`, directiva 3).
5. Añade la fila a la tabla de arriba.
