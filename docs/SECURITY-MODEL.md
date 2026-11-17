# Security Model & Threat Assessment — SkillBridge AI

This document outlines the authentication architecture, authorization controls, session management, CSRF mitigations, and known security vulnerabilities for the SkillBridge AI platform.

---

## 1. Authentication & Session Management

- **Paradigm**: Stateless JWT (JSON Web Token) signed with **RS256** (`io.jsonwebtoken:jjwt`). Only the backend holds the private key (`JWT_PRIVATE_KEY`, no default); other services validate with the public key. Tokens carry `sub` (email), `uid` (user UUID), `email`, `role`, `iss = skillbridge` and a `kid` header. Contract and migration plan: [`contracts/mentorships/security.md`](contracts/mentorships/security.md). Tokens issued before the change were HS256 and are only accepted while `JWT_LEGACY_SECRET` is set.
- **Token Delivery Mechanism**:
  - Primary: HttpOnly, Secure, SameSite=None cookie named `access_token` ([SecurityConfiguration.java:40-44](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L40-L44)).
  - Fallback: Standard `Authorization: Bearer <token>` HTTP header handled by [JwtAuthenticationFilter.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java).
- **Token Lifetime**: 120 minutes (`app.jwt.expiration-minutes: 120`).
- **Session Validation**: Frontend boots up by sending `GET /api/auth/me` with credentials to verify session validity.
- **Passwords**: Hashed with `BCryptPasswordEncoder` (cost factor 10) via [SpringPasswordHasherAdapter.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/security/SpringPasswordHasherAdapter.java).

---

## 2. Authorization & Role-Based Access Control (RBAC)

Spring Security 6 method security is active via `@EnableMethodSecurity`.

### 2.1. User Roles
- `ROLE_CUSTOMER`: Default role upon registration. Can create bookings, cancel own bookings, and request AI recommendations.
- `ROLE_PROVIDER`: Can manage and toggle their own service offerings.
- `ROLE_ADMIN`: Can manage categories, promote user roles, and activate/suspend user accounts.

### 2.2. Route Restriction Matrix

| Method | Endpoint | Authorization Rule | Implementation / Line |
|---|---|---|---|
| `POST` | `/api/auth/login`, `/api/auth/register`, `/api/auth/logout` | `permitAll()` | `SecurityConfiguration.java:83` |
| `GET` | `/api/offerings` | `permitAll()` (Public active catalog) | `SecurityConfiguration.java:87` |
| `GET` | `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**` | `permitAll()` | `SecurityConfiguration.java:85-86` |
| `GET` | `/actuator/prometheus` | `permitAll()` | `SecurityConfiguration.java:88` |
| `POST` | `/api/webhooks/**` | `permitAll()` (Protected by Stripe HMAC) | `SecurityConfiguration.java:84` |
| `GET` | `/api/categories` | **Requires Authentication** *(Discrepancy vs offerings)* | `SecurityConfiguration.java:90` |
| `POST, PUT, DELETE` | `/api/categories/**` | `hasAnyRole('PROVIDER', 'ADMIN')` | `CategoryController.java:46,53,61` |
| `POST` | `/api/offerings` | `hasRole('PROVIDER')` | `OfferingController.java:54` |
| `PUT, PATCH` | `/api/offerings/{id}/**` | `hasAnyRole('PROVIDER', 'ADMIN')` | `OfferingController.java:66,80,97` |
| `GET` | `/api/admin/offerings` | `hasRole('ADMIN')` | `AdminOfferingController.java:24` |
| `GET, PUT` | `/api/admin/users/**` | `hasRole('ADMIN')` | `AdminUserController.java:27` |
| `POST` | `/api/bookings` | `hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')` | `BookingController.java:34` |
| `PATCH` | `/api/bookings/{id}/cancel` | `hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')` | `BookingController.java:43` |
| `POST` | `/api/payments/authorize` | **Requires Authentication** *(No role check or actor check)* | `SecurityConfiguration.java:90` |
| `POST` | `/api/ai/**` | **Requires Authentication** | `SecurityConfiguration.java:90` |

---

## 3. CSRF & CORS Defense

- **CSRF Strategy**:
  - Implemented via `CookieCsrfTokenRepository.withHttpOnlyFalse()` ([SecurityConfiguration.java:41](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L41)).
  - Angular receives cookie `XSRF-TOKEN` and returns header `X-XSRF-TOKEN`.
  - Exemptions: Bearer-authenticated requests and endpoints listed in `.ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/webhooks/**")`.
- **CORS Strategy**:
  - Origins restricted via `app.cors.allowed-origins` (`http://localhost:8088,http://localhost:4200`).
  - `allowCredentials: true` enabled to allow cross-origin cookie passing during development.
  - Allowed headers: `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Idempotency-Key`, `Stripe-Signature`.

---

## 4. Threat Assessment & Vulnerability Registry

The following findings were verified during formal security code audit ([docs/audit/evidence/02-security-review.md](audit/evidence/02-security-review.md)):

| ID | Vulnerability / Threat | Severity | Status | Verified Evidence & Location |
|---|---|---|---|---|
| **SEC-001** | User enumeration via divergent HTTP status codes on login (401 vs 422 vs 403). | Medium | CONFIRMED | [AuthService.java:55-65](../backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java#L55-L65) |
| **SEC-002** | Weak default JWT secret hardcoded in `application.yml`. | High | CONFIRMED in code / HYPOTHESIS in prod | [application.yml:123](../backend/src/main/resources/application.yml#L123) |
| **SEC-003** | **BOLA / IDOR in Payment Authorization**: Any authenticated user can authorize payments on arbitrary `bookingId`s without ownership verification. | High | CONFIRMED | [PaymentController.java:21-35](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java#L21-L35) |
| **SEC-004** | **Unauthenticated Audit Service**: `audit-service/backend` exposes all audit events and metrics without Spring Security or auth filters on port 8081. | High | CONFIRMED | [AuditController.java:18-50](../audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java#L18-L50) |
| **SEC-005** | Excessive Category Permissions: Providers are permitted to delete global categories (`DELETE /api/categories/{id}`). | Medium | CONFIRMED | [CategoryController.java:61](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java#L61) |
| **SEC-006A** | Logout CSRF: `/api/auth/logout` ignores CSRF checks while using cookie-based auth. | Medium | CONFIRMED | [SecurityConfiguration.java:74](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L74) |
| **SEC-007** | Catalog Exposure Inconsistency: `GET /api/categories` requires auth while `GET /api/offerings` is public. | Low | CONFIRMED | [SecurityConfiguration.java:87-90](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L87-L90) |
| **SEC-008** | Lack of JWT Blacklisting / Revocation upon voluntary logout. | Medium | CONFIRMED | [JwtService.java:44](../backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java#L44) |
| **SEC-009** | Cardholder PAN / CVV in Application DTOs: `PaymentRequestDto` takes raw card numbers in transit. | Medium | CONFIRMED (PCI Scope) | [PaymentRequestDto.java:6-12](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/dto/payment/PaymentRequestDto.java#L6-L12) |
| **SEC-010** | Absence of Rate Limiting on authentication and AI endpoints. | Medium | CONFIRMED | [AiController.java:48-72](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java#L48-L72) |

For remediations and verification tests, see the canonical [docs/RISK-REGISTER.md](RISK-REGISTER.md).
