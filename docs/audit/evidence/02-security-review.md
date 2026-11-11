# Informe de Auditoría de Seguridad: Modelo de Autenticación y Autorización — SkillBridge AI

**Destino de evidencia:** `docs/audit/evidence/02-security-review.md`  
**Rol del auditor:** Auditor Senior de Seguridad de Aplicaciones Spring Boot  
**Alcance exclusivo:** Revisión estática de código de seguridad (Spring Security 6 / Spring Boot 3.5.x), configuración de autorización HTTP y a nivel de método, ciclo de vida de tokens/cookies, gestión de CSRF/CORS, controladores REST y contraste con el frontend Angular.  
**Reglas operativas respetadas:** Únicamente investigación estática; no se realizaron cambios de rama, commits, stashes, resets ni modificaciones de código de producto; no se expusieron secretos ni credenciales en texto claro; no se ejecutaron pruebas destructivas ni peticiones externas.

---

## 1. Registro de Metadatos y Contexto de Ejecución

Los siguientes comandos fueron ejecutados en la raíz del repositorio `/home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular`:

```bash
date -u +"%Y-%m-%dT%H:%M:%SZ"
git rev-parse --abbrev-ref HEAD
git rev-parse HEAD
git status --short
```

### Resultados observados

| Variable | Valor observado |
|---|---|
| **Fecha UTC** | `2026-10-21T19:12:09Z` |
| **Rama Git activa** | `develop` |
| **Commit HEAD** | `cdd009aa8c9ba351fa8bb50bb1ad0a381e259793` |
| **Directorio de trabajo** | `/home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular` |

### Salida de `git status --short`

```text
AM docs/audit/findings.md
A  scripts/repo_audit.py
?? docs/audit/evidence/
?? docs/audit/generated/
```

- **Observación sobre el entorno:** No se han alterado archivos existentes de código fuente (`backend/`, `frontend/`, `audit-service/`), pruebas ni `docs/audit/findings.md`. El presente documento se genera exclusivamente en `docs/audit/evidence/02-security-review.md`.

---

## 2. Inspección Completa de la Configuración de Seguridad Central

La clase principal que gobierna la seguridad de la aplicación central es:  
[SecurityConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java) (Líneas 1 a 113).

```java
1: package com.riwi.skillbridge.infrastructure.config;
2: 
3: import com.riwi.skillbridge.infrastructure.security.CsrfCookieFilter;
4: import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
5: import com.riwi.skillbridge.infrastructure.security.RestAccessDeniedHandler;
6: import org.springframework.beans.factory.annotation.Value;
7: import org.springframework.context.annotation.Bean;
8: import org.springframework.context.annotation.Configuration;
9: import org.springframework.http.HttpHeaders;
10: import org.springframework.http.HttpMethod;
11: import org.springframework.http.HttpStatus;
12: import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
13: import org.springframework.security.config.annotation.web.builders.HttpSecurity;
14: import org.springframework.security.config.http.SessionCreationPolicy;
15: import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
16: import org.springframework.security.crypto.password.PasswordEncoder;
17: import org.springframework.security.core.Authentication;
18: import org.springframework.security.core.context.SecurityContextHolder;
19: import org.springframework.security.authentication.AnonymousAuthenticationToken;
20: import org.springframework.security.web.SecurityFilterChain;
21: import org.springframework.security.web.authentication.HttpStatusEntryPoint;
22: import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
23: import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
24: import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
25: import org.springframework.security.web.csrf.CsrfFilter;
26: import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
27: import org.springframework.security.web.util.matcher.RequestMatcher;
28: import org.springframework.web.cors.CorsConfiguration;
29: import org.springframework.web.cors.CorsConfigurationSource;
30: import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
31: 
32: import java.util.Arrays;
33: import java.util.List;
34: import java.util.Set;
35: 
36: @Configuration
37: @EnableMethodSecurity
38: public class SecurityConfiguration {
39: 
40:     private CookieCsrfTokenRepository cookieCsrfTokenRepository() {
41:         CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
42:         repo.setCookieCustomizer(customizer -> customizer.sameSite("None").secure(true));
43:         return repo;
44:     }
45:     @Bean
46:     SecurityFilterChain security(HttpSecurity http,
47:                                  JwtAuthenticationFilter jwtFilter,
48:                                  RestAccessDeniedHandler accessDeniedHandler) throws Exception {
49:         CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
50:         csrfHandler.setCsrfRequestAttributeName("_csrf");
51: 
52:         // Peticiones con Bearer no usan cookie, así que no son vulnerables a CSRF
53:         RequestMatcher bearerRequests = request -> {
54:             String header = request.getHeader(HttpHeaders.AUTHORIZATION);
55:             return header != null && header.startsWith("Bearer ");
56:         };
57:         RequestMatcher cookieAuthenticatedUnsafeRequests = request -> {
58:             if (Set.of("GET", "HEAD", "TRACE", "OPTIONS").contains(request.getMethod())
59:                 || bearerRequests.matches(request)) {
60:                 return false;
61:             }
62:             Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
63:             return authentication != null
64:                 && authentication.isAuthenticated()
65:                 && !(authentication instanceof AnonymousAuthenticationToken);
66:         };
67: 
68:         return http
69:             .csrf(csrf -> csrf
70:                 .csrfTokenRepository(cookieCsrfTokenRepository())
71:                 .csrfTokenRequestHandler(csrfHandler)
72:                 .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
73:                 .requireCsrfProtectionMatcher(cookieAuthenticatedUnsafeRequests)
74:                 .ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/webhooks/**")
75:                 .ignoringRequestMatchers(bearerRequests))
76:             .cors(cors -> {
77:             })
78:             .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
79:             .exceptionHandling(e -> e
80:                 .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
81:                 .accessDeniedHandler(accessDeniedHandler))
82:             .authorizeHttpRequests(auth -> auth
83:                 .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout",
84:                     "/api/webhooks/**",
85:                     "/actuator/health", "/actuator/health/**", "/v3/api-docs/**",
86:                     "/swagger-ui/**", "/swagger-ui.html").permitAll()
87:                 .requestMatchers(HttpMethod.GET, "/api/offerings").permitAll()
88:                 .requestMatchers("/actuator/prometheus").permitAll()
89:                 .requestMatchers("/error").permitAll()
90:                 .anyRequest().authenticated())
91:             .addFilterBefore(jwtFilter, CsrfFilter.class)
92:             .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
93:             .build();
94:     }
95: 
96:     @Bean
97:     PasswordEncoder passwordEncoder() {
98:         return new BCryptPasswordEncoder();
99:     }
100: 
101:     @Bean
102:     CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
103:         CorsConfiguration config = new CorsConfiguration();
104:         config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
105:         config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
106:         config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "Stripe-Signature"));
107:         config.setExposedHeaders(List.of("X-XSRF-TOKEN"));
108:         config.setAllowCredentials(true);
109:         UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
110:         source.registerCorsConfiguration("/**", config);
111:         return source;
112:     }
113: }
```

### Análisis Técnico de Componentes de `SecurityConfiguration`

1. **Anotaciones de Arquitectura (Líneas 36-37):**
   - `@Configuration`: Declara la clase como fuente de beans de Spring.
   - `@EnableMethodSecurity`: Activa la evaluación previa y posterior de autorizaciones mediante anotaciones como `@PreAuthorize`, `@PostAuthorize`, `@PreFilter` y `@PostFilter` en controladores y servicios de aplicación.
2. **Estrategia de Sesión (Línea 78):**
   - `SessionCreationPolicy.STATELESS`: Spring Security no creará ni utilizará sesiones HTTP (`HttpSession`) del contenedor Servlet.
3. **Manejo de Excepciones de Seguridad (Líneas 79-81):**
   - `authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))`: Cualquier petición no autenticada dirigida a una ruta protegida recibe un código HTTP `401 Unauthorized`.
   - `accessDeniedHandler(accessDeniedHandler)`: Utiliza [RestAccessDeniedHandler.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/RestAccessDeniedHandler.java#L16-L34), el cual serializa un objeto estándar RFC 7807 (`ProblemDetail`) con estado `403 Forbidden` y mensaje `"No tienes permisos para realizar esta operación"`.
4. **Orden de Filtros en la Cadena de Seguridad (Líneas 91-92):**
   - `addFilterBefore(jwtFilter, CsrfFilter.class)`: Ubica a `JwtAuthenticationFilter` antes de `CsrfFilter`. Esto es crítico: cuando `CsrfFilter` evalúa la petición, la autenticación ya ha sido inyectada en el `SecurityContextHolder` si el token es válido.
   - `addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)`: Ubica a `CsrfCookieFilter` justo después del filtro CSRF para forzar la carga del token (`csrfToken.getToken()`) y exponerlo en la cabecera HTTP de respuesta `X-XSRF-TOKEN`.
5. **Codificador de Contraseñas (Líneas 97-99):**
   - Define un bean `BCryptPasswordEncoder()` (fuerza de cálculo por defecto log_rounds = 10).
6. **Configuración CORS (Líneas 102-112):**
   - Define un bean `CorsConfigurationSource` registrado para la ruta `"/**"`. Permite credenciales (`allowCredentials=true`), orígenes configurados dinámicamente mediante la propiedad `${app.cors.allowed-origins}`, y expone la cabecera `X-XSRF-TOKEN`.

---

## 3. Mecanismo Efectivo de Autenticación

El flujo de autenticación opera bajo un esquema híbrido de **JWT en Cookie HttpOnly con fallback a cabecera Bearer**:

```
[Cliente HTTP / Navegador]
       │
       ▼ (1) POST /api/auth/login o /api/auth/register
[AuthController] ──▶ [AuthService] ──▶ Valida BCrypt / Persiste Usuario
       │                                         │
       ▼                                         ▼
   Crea Cookie HttpOnly                    Genera JWT firmado
   (access_token)                         (sub=email, role=ROLE)
       │
       ▼ (2) Siguientes peticiones HTTP con Cookie o Header "Authorization: Bearer <token>"
[JwtAuthenticationFilter] (OncePerRequestFilter)
       ├─▶ AuthCookieService.resolve(request) ──▶ Lee cookie "access_token"
       └─▶ Fallback: Cabecera "Authorization: Bearer ..."
       │
       ├─▶ JwtService.extractUsername(token)
       ├─▶ DatabaseUserDetailsService.loadUserByUsername(email) ──▶ Consulta DB (PostgreSQL)
       ├─▶ Verifica user.isEnabled() [status != SUSPENDED] y JwtService.isValid(token, user)
       └─▶ SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken)
```

### 3.1. Cadena de Filtros y Resolución de Tokens

En [JwtAuthenticationFilter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java#L30-L60):

- **Método `resolveToken(HttpServletRequest request)` (Líneas 54-58):**
  ```java
  return cookieService.resolve(request).orElseGet(() -> {
      String header = request.getHeader("Authorization");
      return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
  });
  ```
  **Prioridad:** Primero inspecciona la presencia de la cookie `access_token` a través de `AuthCookieService`. Solo si no existe cookie o su valor está en blanco, recurre a la cabecera `Authorization: Bearer <token>`.
- **Validación del Token (Líneas 38-49):**
  - Si el token está presente, extrae el `sub` (correo del usuario).
  - Consulta `DatabaseUserDetailsService.loadUserByUsername(username)`.
  - Comprueba dos condiciones indispensables: `user.isEnabled()` y `jwtService.isValid(token, user)`.
  - Si ambas son válidas, instancia un `UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())` y lo establece en el contexto de seguridad.
  - En caso de excepción (token expirado, firma inválida, usuario inexistente o suspendido), captura el error y ejecuta `SecurityContextHolder.clearContext()`.

### 3.2. Ciclo de Vida y Propiedades de Cookies

En [AuthCookieService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/AuthCookieService.java#L22-L57):

- **Nombre de cookie:** `access_token` (configurable por `app.jwt.cookie.name`).
- **Ruta (`path`):** Fija a `COOKIE_PATH = "/api"` (Línea 15).
- **Atributos de seguridad:**
  - `httpOnly(true)`: Mitiga el robo de tokens por XSS directo en el navegador (Línea 53).
  - `secure(secure)`: Por defecto `true` (requiere HTTPS).
  - `sameSite(sameSite)`: El constructor toma por defecto `Lax` si no se provee valor (`@Value("${app.jwt.cookie.same-site:Lax}")`), pero en [application.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/resources/application.yml#L128) se encuentra configurado explícitamente:
    `app.jwt.cookie.same-site: ${JWT_COOKIE_SAME_SITE:None}`
- **Duración (`maxAge`):** 120 minutos (`app.jwt.expiration-minutes: 120`).
- **Destrucción de cookie (`clear()`):** Establece valor vacío y `maxAge(Duration.ZERO)` al invocar `POST /api/auth/logout`.

### 3.3. Estructura y Validación Criptográfica del Token JWT

En [JwtService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java#L18-L57):

- **Algoritmo:** HMAC-SHA utilizando `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))` (JJWT 0.12.x). Requiere una clave mínima de 256 bits (32 caracteres UTF-8).
- **Contenido del Payload:**
  - `sub`: Correo electrónico del usuario (normalizado a minúsculas).
  - `role`: Rol del usuario (`CUSTOMER`, `PROVIDER`, `ADMIN`).
  - `iat`: Instante de emisión (`Instant.now()`).
  - `exp`: Instante de expiración (`now + expirationMinutes` [120 minutos por defecto]).
- **Validación:**
  - Verifica que el subject coincida sin distinguir mayúsculas/minúsculas con el nombre del usuario recuperado de la base de datos (`claims.getSubject().equalsIgnoreCase(userDetails.getUsername())`).
  - Verifica que `claims.getExpiration().after(new Date())`.
  - La firma se comprueba mediante `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`.

### 3.4. Almacenamiento y Validación de Credenciales

1. **Persistencia de Usuarios:**
   - Tabla `app_users` ([V1__init.sql](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/resources/db/migration/V1__init.sql#L1-L8)):
     - Columnas: `id` (UUID), `name` (VARCHAR 120), `email` (VARCHAR 180 UNIQUE), `password` (VARCHAR 255), `role` (VARCHAR 30), `created_at` (TIMESTAMPTZ).
     - Columna `status` agregada en [V5__user_status.sql](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/resources/db/migration/V5__user_status.sql#L1-L2): `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED'))`.
2. **Carga en Spring Security ([DatabaseUserDetailsService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/DatabaseUserDetailsService.java#L15-L23)):**
   - Recupera el usuario con `users.findByEmailIgnoreCase(username)`.
   - Asigna los roles mediante `roles(user.getRole().name())` (Spring antepone automáticamente el prefijo `ROLE_`).
   - Bloquea cuentas con `disabled(user.getStatus() == UserStatus.SUSPENDED)`.
   - **Comportamiento en tiempo de ejecución:** Dado que `JwtAuthenticationFilter` consulta `DatabaseUserDetailsService` en cada petición, la suspensión de un usuario surte efecto de forma inmediata, impidiendo la autenticación incluso con un token JWT no expirado.
3. **Password Encoder ([SpringPasswordHasherAdapter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/SpringPasswordHasherAdapter.java#L8-L13)):**
   - Encapsula el bean `BCryptPasswordEncoder` definido en `SecurityConfiguration.java:97-99`.
   - Se utiliza tanto en `AuthService.register()` para el hash de la contraseña cruda como en `AuthService.login()` para la verificación con `matches()`.

---

## 4. Mapeo de Reglas HTTP de Autorización vs Controladores Reales

A continuación se contrasta cada regla configurada a nivel de filtro HTTP en `SecurityConfiguration.java` con las anotaciones de seguridad por método (`@PreAuthorize`) y la lógica de validación interna en los casos de uso:

| Endpoint | Método HTTP | Regla en `SecurityConfiguration` | Anotación en Controlador | Autorización Efectiva Final | Verificación de Pertenencia / Dominio |
|---|---|---|---|---|---|
| `/api/auth/register` | `POST` | `permitAll()` (L. 83) | Ninguna | Pública (Anónima) | Asigna rol fijo `CUSTOMER` |
| `/api/auth/login` | `POST` | `permitAll()` (L. 83) | Ninguna | Pública (Anónima) | Valida credenciales y estado |
| `/api/auth/logout` | `POST` | `permitAll()` (L. 83) | Ninguna | Pública (Anónima) | Borra cookie en cliente |
| `/api/auth/me` | `GET` | `anyRequest().authenticated()` | Ninguna | Autenticado (Cualquier rol) | Devuelve datos del token |
| `/api/offerings` | `GET` | `permitAll()` (L. 87) | Ninguna | Pública (Anónima) | Consulta catálogo activo |
| `/api/offerings/me` | `GET` | `anyRequest().authenticated()` | `@PreAuthorize("hasRole('PROVIDER')")` | Solo rol `PROVIDER` | Filtrado por `actor.id()` |
| `/api/offerings` | `POST` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` | Roles `PROVIDER`, `ADMIN` | Asigna `actor.id()` o valida `providerId` |
| `/api/offerings/{id}` | `PUT` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` | Roles `PROVIDER`, `ADMIN` | `OfferingAccessPolicy`: dueño o admin |
| `/api/offerings/{id}/deactivate` | `POST` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` | Roles `PROVIDER`, `ADMIN` | `OfferingAccessPolicy`: dueño o admin |
| `/api/offerings/{id}/activate` | `POST` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` | Roles `PROVIDER`, `ADMIN` | `OfferingAccessPolicy`: dueño o admin |
| `/api/admin/offerings` | `GET` | `anyRequest().authenticated()` | `@PreAuthorize("hasRole('ADMIN')")` | Solo rol `ADMIN` | Consulta total sin filtro |
| `/api/admin/users` | `GET` | `anyRequest().authenticated()` | Clase `@PreAuthorize("hasRole('ADMIN')")` | Solo rol `ADMIN` | Paginado de usuarios |
| `/api/admin/users/{id}` | `GET` | `anyRequest().authenticated()` | Clase `@PreAuthorize("hasRole('ADMIN')")` | Solo rol `ADMIN` | Detalle de usuario |
| `/api/admin/users/{id}/status` | `PUT` | `anyRequest().authenticated()` | Clase `@PreAuthorize("hasRole('ADMIN')")` | Solo rol `ADMIN` | Regla: no suspenderse a sí mismo |
| `/api/admin/users/{id}/role` | `PUT` | `anyRequest().authenticated()` | Clase `@PreAuthorize("hasRole('ADMIN')")` | Solo rol `ADMIN` | Regla: no cambiar propio rol |
| `/api/categories` | `GET` | `anyRequest().authenticated()` | Ninguna | **Autenticado (Cualquier rol)** *(Inconsistencia con catálogo)* | No |
| `/api/categories` | `POST` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` | Roles `PROVIDER`, `ADMIN` | No (Crea categoría global) |
| `/api/categories/{id}` | `PUT` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` | Roles `PROVIDER`, `ADMIN` | No (Modifica categoría global) |
| `/api/categories/{id}` | `DELETE` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` | Roles `PROVIDER`, `ADMIN` | **No (Cualquier PROVIDER puede borrar categorías globales)** |
| `/api/bookings` | `POST` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('CUSTOMER','PROVIDER','ADMIN')")` | Roles `CUSTOMER`, `PROVIDER`, `ADMIN` | Valida oferta activa, asocia al email |
| `/api/bookings/me` | `GET` | `anyRequest().authenticated()` | `@PreAuthorize("hasAnyRole('CUSTOMER','PROVIDER','ADMIN')")` | Roles `CUSTOMER`, `PROVIDER`, `ADMIN` | Filtra por email del actor |
| `/api/bookings/{bookingId}/cancel` | `PATCH` | `anyRequest().authenticated()` | Ninguna | Autenticado (Cualquier rol) | `BookingService`: valida estricto `customerId == booking.customerId` |
| `/api/payments/authorize` | `POST` | `anyRequest().authenticated()` | **Ninguna** | **Autenticado (Cualquier rol)** | **No se valida si `bookingId` pertenece al llamante** |
| `/api/webhooks/stripe` | `POST` | `permitAll()` (L. 84) | Ninguna | Pública (Sin autenticación HTTP) | Firma criptográfica Stripe HMAC-SHA256 (`Stripe-Signature`) |
| `/api/ai/recommendations` | `POST` | `anyRequest().authenticated()` | Ninguna | Autenticado (Cualquier rol) | Asocia recomendación a `actor.id()` |
| `/api/ai/recommendations/voice`| `POST` | `anyRequest().authenticated()` | Ninguna | Autenticado (Cualquier rol) | Asocia recomendación a `actor.id()` |
| `/api/ai/recommendations/image`| `POST` | `anyRequest().authenticated()` | Ninguna | Autenticado (Cualquier rol) | Asocia recomendación a `actor.id()` |
| `/api/ai/tts` | `POST` | `anyRequest().authenticated()` | Ninguna | Autenticado (Cualquier rol) | No (Generador de voz abierto a autenticados) |
| `/actuator/health/**` | `GET` | `permitAll()` (L. 85) | Ninguna | Pública (Health probes) | `show-details: when_authorized` |
| `/actuator/prometheus` | `GET` | `permitAll()` (L. 88) | Ninguna | Pública (Scraping de métricas) | Métricas expuestas para Prometheus |
| `/v3/api-docs/**`, `/swagger-ui/**` | `GET` | `permitAll()` (L. 85-86) | Ninguna | Pública | Documentación OpenAPI Swagger |
| `/error` | `*` | `permitAll()` (L. 89) | Ninguna | Pública | Despacho de errores Servlet |

### 4.1. Análisis del Modelo de Roles y Fuentes de Identidad

1. **Definición de Roles:**
   - [Role.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/domain/model/Role.java#L3-L7): `CUSTOMER`, `PROVIDER`, `ADMIN`.
2. **Transformación a Autoridades Spring:**
   - `DatabaseUserDetailsService.java`: `.roles(user.getRole().name())` genera internamente autoridades con prefijo `ROLE_` (`ROLE_CUSTOMER`, `ROLE_PROVIDER`, `ROLE_ADMIN`).
3. **Resolución de Dominio (`CurrentActorResolver`):**
   - [CurrentActorResolver.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/CurrentActorResolver.java#L15-L33): Extrae el email de `authentication.getName()`, consulta el `UUID` en la base de datos a través de `UserAccountPort.findIdByEmail(email)` y reconstruye el objeto inmutable `Actor(id, email, role)`.

---

## 5. Revisión de CORS, CSRF, Exposición de Datos y Frontend

### 5.1. Mecanismo de Protección CSRF

- **Estrategia configurada:** Se utiliza `CookieCsrfTokenRepository.withHttpOnlyFalse()` combinado con un customizer que aplica `SameSite=None` y `Secure=true` ([SecurityConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L40-L44)).
- **Matcher de Protección (`cookieAuthenticatedUnsafeRequests`, Líneas 57-66):**
  - Excluye métodos seguros (`GET`, `HEAD`, `TRACE`, `OPTIONS`).
  - Excluye peticiones con cabecera `Authorization: Bearer ...` (bajo la premisa de que clientes de API pura como Postman o mobile no sufren CSRF ya que el navegador no adjunta cabeceras automáticamente).
  - Activa la verificación de CSRF **únicamente si** el contexto de seguridad contiene una autenticación activa no anónima generada mediante cookie.
- **Sincronización con el Frontend Angular:**
  - En navegadores donde el frontend corre en un origen cruzado (ej. `http://localhost:4200` o Vercel) y el backend en otro (ej. `http://localhost:8080` o Render), las cookies configuradas en el backend no son legibles vía `document.cookie` debido a la Política del Mismo Origen (SOP) de Cookies.
  - Para solventar esto, [CsrfCookieFilter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/CsrfCookieFilter.java#L12-L26) extrae el token generado y lo escribe en la cabecera HTTP de respuesta `X-XSRF-TOKEN`.
  - En el cliente Angular, [auth.interceptor.ts](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/frontend/src/app/core/auth.interceptor.ts#L16-L41) intercepta la respuesta, almacena en memoria la variable `csrfToken = token` y la adjunta automáticamente como cabecera `X-XSRF-TOKEN` en todas las peticiones `POST`, `PUT`, `PATCH` y `DELETE`.
  - La propiedad `CorsConfiguration.setExposedHeaders(List.of("X-XSRF-TOKEN"))` en el backend hace accesible esta cabecera para el JavaScript del navegador.

### 5.2. Evaluación de CORS

- En [SecurityConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L102-L112):
  - `allowedOrigins`: Se parsea desde la variable `app.cors.allowed-origins` (`${CORS_ALLOWED_ORIGINS:http://localhost:8088,http://localhost:4200}`).
  - `allowedMethods`: `"GET"`, `"POST"`, `"PUT"`, `"PATCH"`, `"DELETE"`, `"OPTIONS"`.
  - `allowedHeaders`: `"Authorization"`, `"Content-Type"`, `"X-XSRF-TOKEN"`, `"Idempotency-Key"`, `"Stripe-Signature"`.
  - `allowCredentials`: `true`.
  - **Dictamen:** No se detectan comodines inseguros (`*`) en combinación con `allowCredentials=true`.

### 5.3. Exposición de Datos y Manejo de Errores

- En [GlobalExceptionHandler.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java#L15-L68):
  - Los errores se estandarizan con RFC 7807 (`ProblemDetail`).
  - No se exponen trazas de pila (stacktraces) internas en las respuestas JSON hacia clientes externos.

---

## 6. Arquitectura Satélite: Microservicio `audit-service`

Se realizó la revisión estática del microservicio complementario ubicado en `audit-service/backend/`:
- **Dependencias Maven:** En [audit-service/backend/pom.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/pom.xml#L23-L45), **no existe la dependencia `spring-boot-starter-security`**.
- **Controlador expuesto:** [AuditController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java#L18-L50) expone los siguientes endpoints:
  - `GET /api/audit/events`: Retorna la lista histórica completa de eventos de negocio ordenados por fecha descendente.
  - `GET /api/audit/events/{eventId}`: Retorna el detalle del evento de negocio por UUID.
  - `GET /api/audit/stats`: Retorna métricas cuantitativas agregadas de eventos.
- **Configuración CORS:** [CorsConfig.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/config/CorsConfig.java#L18-L22) habilita `allowedHeaders("*")` y métodos estándar para todas las rutas.
- **Dictamen:** `audit-service` carece de capa de autenticación y autorización. Si este servicio es expuesto a la red o mapeado a través de un reverse proxy sin control previo, cualquier entidad externa puede volcar el historial de auditoría de la plataforma (incluyendo identificadores de usuarios, correos electrónicos y metadatos de reservas).

---

## 7. Registro Consolidado de Hallazgos de Seguridad

| ID | Hallazgo | Severidad | Estado | Componente Afectado |
|---|---|---|---|---|
| **SEC-001** | Enumeración de usuarios por discrepancia de respuestas HTTP en autenticación | MEDIA | CONFIRMADO | `AuthService.java` / `GlobalExceptionHandler.java` |
| **SEC-002** | Secreto JWT débil por defecto codificado en `application.yml` | ALTA | CONFIRMADO (código) / HIPÓTESIS (despliegue) | `application.yml` / `JwtService.java` |
| **SEC-003** | Vulnerabilidad BOLA/IDOR en autorización de pagos (`/api/payments/authorize`) | ALTA | CONFIRMADO | `PaymentController.java` / `PaymentAuthorizationService.java` |
| **SEC-004** | Ausencia total de autenticación y control de acceso en microservicio `audit-service` | ALTA | CONFIRMADO | `audit-service/backend/` |
| **SEC-005** | Control de acceso horizontal permisivo: Eliminación de categorías globales por rol `PROVIDER` | MEDIA | CONFIRMADO | `CategoryController.java` / `CategoryService.java` |
| **SEC-006** | Logout vulnerable a CSRF y desincronización en resolución de tokens Bearer vs Cookies | MEDIA | CONFIRMADO (Logout CSRF) / HIPÓTESIS (Bypass) | `SecurityConfiguration.java` / `JwtAuthenticationFilter.java` |
| **SEC-007** | Inconsistencia de autorización en catálogo: `GET /api/categories` protegido vs `GET /api/offerings` público | BAJA | CONFIRMADO | `SecurityConfiguration.java` / `CategoryController.java` |
| **SEC-008** | Ausencia de lista de revocación (Blacklist) para tokens JWT emitidos ante Logout | MEDIA | CONFIRMADO | `AuthController.java` / `JwtService.java` |
| **SEC-009** | Tránsito de datos de tarjeta de crédito en texto claro en capa de aplicación (`PaymentRequestDto`) | MEDIA | CONFIRMADO | `PaymentController.java` / `PaymentRequestDto.java` |
| **SEC-010** | Ausencia de limitación de tasa (Rate Limiting) en autenticación y llamadas a IA | MEDIA | CONFIRMADO | `SecurityConfiguration.java` / `AuthController.java` / `AiController.java` |

---

## 8. Fichas Detalladas de Hallazgos de Seguridad

### SEC-001: Enumeración de usuarios por discrepancia de respuestas HTTP en autenticación

- **Severidad:** MEDIA (CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N — Base Score: 5.3)
- **Estado:** CONFIRMADO
- **Evidencia con Rutas y Líneas:**
  - [AuthService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java#L55-L65):
    ```java
    55:     public String login(String email, String rawPassword) {
    56:         UserAccount user = users.findByEmail(email.trim().toLowerCase())
    57:             .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas"));
    58:         if (!passwords.matches(rawPassword, user.passwordHash())) {
    59:             throw new InvalidCredentialsException("Credenciales inválidas");
    60:         }
    61:         if (user.status() == UserStatus.SUSPENDED) {
    62:             throw new ForbiddenOperationException("La cuenta está suspendida");
    63:         }
    ```
  - [GlobalExceptionHandler.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java#L24-L36):
    ```java
    24:     @ExceptionHandler(BusinessRuleException.class)
    25:     ProblemDetail business(BusinessRuleException ex) {
    26:         ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    ...
    31:     @ExceptionHandler(InvalidCredentialsException.class)
    32:     ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
    33:         ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    ...
    48:     @ExceptionHandler(ForbiddenOperationException.class)
    49:     ProblemDetail forbidden(ForbiddenOperationException ex) {
    50:         ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    ```
  - [AuthService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java#L38-L40) en registro:
    ```java
    38:         if (users.existsByEmail(normalizedEmail)) {
    39:             throw new BusinessRuleException("El correo ya está registrado");
    40:         }
    ```
- **Escenario de Impacto:**
  Un atacante externo no autenticado puede enviar peticiones automatizadas a `POST /api/auth/login`. Si el correo electrónico no existe en el sistema, el backend responde con código HTTP `422 Unprocessable Entity`. Si el correo existe pero la contraseña es errónea, el backend responde con código HTTP `401 Unauthorized`. Si el correo pertenece a un usuario suspendido, responde con `403 Forbidden`. Esto permite cosechar de forma inequívoca el directorio de correos válidos de la organización para lanzar ataques de credential stuffing, password spraying o ingeniería social dirigida.
- **Recomendación:**
  1. En `AuthService.login()`, si el usuario no existe, lanzar igualmente `InvalidCredentialsException("Credenciales inválidas")` en lugar de `BusinessRuleException`.
  2. Implementar una comparación de contraseñas ficticia o en tiempo constante ante usuarios inexistentes para evitar discrepancias temporales de respuesta (Timing Attacks).
  3. Ambos casos deben retornar uniformemente HTTP `401 Unauthorized`.
- **Comprobación Pendiente:**
  Verificar que las suites de pruebas unitarias y de integración de `AuthController` contemplen el retorno consistente de HTTP `401` para cualquier falla de credenciales.

---

### SEC-002: Secreto JWT débil por defecto codificado en `application.yml`

- **Severidad:** ALTA (CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:H — Base Score: 8.1)
- **Estado:** CONFIRMADO en código fuente / HIPÓTESIS en tiempo de ejecución (depende de variables de entorno inyectadas en despliegue)
- **Evidencia con Rutas y Líneas:**
  - [application.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/resources/application.yml#L122-L124):
    ```yaml
    122: app:
    123:   jwt:
    124:     secret: ${JWT_SECRET:change-this-to-a-long-random-secret-at-least-32-characters}
    ```
  - [JwtService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java#L22-L27):
    ```java
    22:     public JwtService(@Value("${app.jwt.secret}") String secret,
    23:                       @Value("${app.jwt.expiration-minutes:120}") long expirationMinutes) {
    24:         if (secret.length() < 32) throw new IllegalArgumentException("JWT secret must have at least 32 characters");
    25:         this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    26:         this.expirationMinutes = expirationMinutes;
    27:     }
    ```
- **Escenario de Impacto:**
  El valor de fallback tiene 56 caracteres, por lo que supera la validación programática `secret.length() < 32`. Si un contenedor Docker o entorno de producción se despliega sin suministrar la variable de entorno `JWT_SECRET`, la aplicación levantará de forma silenciosa utilizando el secreto por defecto del repositorio. Un atacante con conocimiento del código fuente público o compartido puede firmar tokens JWT arbitrarios forjando el claim `"role": "ADMIN"` y el correo de cualquier usuario, logrando una toma total de control administrativo (Account Takeover) sin pasar por el flujo de autenticación.
- **Recomendación:**
  1. Eliminar el valor por defecto en `application.yml`: `secret: ${JWT_SECRET}` (forzando que el contexto de Spring falle al arrancar si la variable no está definida).
  2. Implementar una validación en `JwtService` que prohíba explícitamente cadenas conocidas como `"change-this-to-a-long-random-secret-at-least-32-characters"`.
- **Comprobación Pendiente:**
  Verificar la configuración de infraestructura y despliegue (Docker Compose, Kubernetes manifests, variables de entorno en Render/AWS) para constatar si `JWT_SECRET` está efectivamente inyectada como variable protegida.

---

### SEC-003: Vulnerabilidad BOLA/IDOR en autorización de pagos (`/api/payments/authorize`)

- **Severidad:** ALTA (CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:N — Base Score: 6.5)
- **Estado:** CONFIRMADO
- **Evidencia con Rutas y Líneas:**
  - [PaymentController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java#L21-L45):
    ```java
    21:     @PostMapping("/authorize")
    22:     public ResponseEntity<PaymentResponseDto> authorizePayment(
    23:             @RequestHeader("Idempotency-Key") String idempotencyKey,
    24:             @RequestBody PaymentRequestDto request) {
    25:             
    26:         PaymentCommand command = new PaymentCommand(
    27:                 idempotencyKey,
    28:                 request.bookingId(),
    29:                 request.amount(),
    30:                 request.cardNumber(),
    31:                 request.expiryDate(),
    32:                 request.cvv()
    33:         );
    34: 
    35:         PaymentResult result = authorizePaymentUseCase.authorize(command);
    ...
    ```
  - [PaymentAuthorizationService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/PaymentAuthorizationService.java#L33-L46):
    ```java
    33:     public PaymentResult authorize(PaymentCommand command) {
    ...
    42:         if (result.status() == PaymentStatus.APPROVED) {
    43:             eventPublisherPort.publishApproved(new PaymentApproved(result.bookingId()));
    44:         }
    ```
  - [BookingService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java#L160-L177):
    ```java
    160:     public void processPaymentApproved(UUID bookingId) {
    161:         Booking booking = bookingRepository.findById(bookingId)
    ...
    173:             BookingStatus.CONFIRMED,
    ...
    177:         bookingRepository.save(confirmedBooking);
    178:     }
    ```
- **Escenario de Impacto:**
  El endpoint `POST /api/payments/authorize` solo requiere estar autenticado ante Spring Security (`anyRequest().authenticated()`). No recibe ni evalúa el objeto `Authentication` del usuario que realiza la llamada. Tampoco valida si el `bookingId` suministrado en el payload pertenece al usuario autenticado. Un usuario con rol `CUSTOMER` legítimo puede autorizar pagos, vincular cobros o alterar el ciclo de vida de una reserva ajena a estado `CONFIRMED` simplemente conociendo o interceptando su `bookingId`.
- **Recomendación:**
  1. Inyectar `Authentication authentication` en `PaymentController.authorizePayment()`.
  2. Resolver el actor actual con `CurrentActorResolver` y transferir el `actor` o su `id` al caso de uso `AuthorizePaymentUseCase`.
  3. En `PaymentAuthorizationService`, consultar la reserva por `command.bookingId()` y verificar estrictamente que `booking.customerId().equals(actor.id())` (o permitir la operación a administradores). Si no coincide, rechazar con excepción de autorización (`DomainNotFoundException` o `ForbiddenOperationException`).
- **Comprobación Pendiente:**
  Diseñar una prueba automatizada en `BookingControllerSecurityTest` o en una nueva suite `PaymentControllerSecurityTest` que intente autorizar el pago de una reserva perteneciente a otro usuario y espere un código HTTP `403` o `404`.

---

### SEC-004: Ausencia total de autenticación y control de acceso en microservicio `audit-service`

- **Severidad:** ALTA (CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N — Base Score: 7.5)
- **Estado:** CONFIRMADO a nivel de código fuente
- **Evidencia con Rutas y Líneas:**
  - [audit-service/backend/pom.xml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/pom.xml#L23-L45):
    Ausencia total del starter `spring-boot-starter-security`.
  - [AuditController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java#L18-L50):
    ```java
    18: @RequestMapping("/api/audit")
    ...
    24:     @GetMapping("/events")
    25:     public List<BusinessEvent> getEvents() {
    26:         return auditEventRepository.findAllByOrderByOccurredAtDesc();
    27:     }
    28: 
    29:     @GetMapping("/events/{eventId}")
    30:     public ResponseEntity<BusinessEvent> getEvent(@PathVariable UUID eventId) {
    ...
    36:     @GetMapping("/stats")
    37:     public Map<String, Object> getStats() {
    ```
- **Escenario de Impacto:**
  El servicio de auditoría almacena eventos generados por Kafka ([BusinessEvent.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java#L1-L32)), que incluyen correos de usuarios (`actorEmail`), identificadores de usuario (`actorId`), acciones realizadas (`action`), tipo de entidad (`entityType`), IDs de entidad y detalles de reservas. Al no tener filtros de autenticación ni seguridad configurada, cualquier usuario o proceso con acceso a la red donde se ejecute el servicio (puerto 8081 por defecto) puede descargar de manera irrestricta la totalidad de los registros de auditoría y estadísticas del negocio.
- **Recomendación:**
  1. Incorporar `spring-boot-starter-security` en `audit-service/backend/pom.xml`.
  2. Implementar un filtro de validación de tokens JWT o autenticación mTLS / Service-to-Service Secret entre el frontend administrativo / API Gateway y el `audit-service`.
  3. Restringir el acceso a los endpoints `/api/audit/**` exclusivamente a usuarios con rol `ADMIN`.
- **Comprobación Pendiente:**
  Verificar la topología de red en producción (ej. configuración de Docker Compose / Kubernetes NetworkPolicies) para determinar si el puerto 8081 está expuesto directamente a Internet o aislado en una red privada interna.

---

### SEC-005: Control de acceso horizontal permisivo: Eliminación de categorías globales por rol `PROVIDER`

- **Severidad:** MEDIA (CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:H/A:L — Base Score: 7.1)
- **Estado:** CONFIRMADO
- **Evidencia con Rutas y Líneas:**
  - [CategoryController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java#L44-L65):
    ```java
    44:     @PostMapping
    45:     @ResponseStatus(HttpStatus.CREATED)
    46:     @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    ...
    52:     @PutMapping("/{id}")
    53:     @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    ...
    59:     @DeleteMapping("/{id}")
    60:     @ResponseStatus(HttpStatus.NO_CONTENT)
    61:     @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    62:     @Operation(summary = "Elimina una categoría")
    63:     public void delete(@PathVariable UUID id) {
    64:         deleteCategory.delete(id);
    65:     }
    ```
  - [CategoryService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/CategoryService.java#L58-L64):
    ```java
    58:     @Override
    59:     public void delete(UUID id) {
    60:         if (repository.findById(id).isEmpty()) {
    61:             throw new BusinessRuleException("La categoría no existe");
    62:         }
    63:         repository.delete(id);
    64:     }
    ```
- **Escenario de Impacto:**
  Las categorías representan una entidad taxonómica global para todo el catálogo de servicios de SkillBridge. Al otorgar permisos de mutación y borrado a `hasAnyRole('PROVIDER','ADMIN')`, cualquier usuario registrado que obtenga el rol `PROVIDER` puede modificar el nombre de categorías existentes o eliminarlas por completo. Esto permite que un proveedor malicioso borre categorías utilizadas por ofertas de competidores o degrade la integridad del catálogo de la plataforma.
- **Recomendación:**
  1. Restringir la creación, edición y borrado de categorías (`POST`, `PUT`, `DELETE` en `CategoryController`) estrictamente al rol `ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`).
  2. Si se desea permitir sugerencias de categorías por parte de proveedores, implementar un flujo de aprobación previa.
  3. En `CategoryService.delete()`, validar adicionalmente que no existan ofertas activas vinculadas a la categoría antes de proceder con el borrado físico o implementar un borrado lógico (`active = false`).
- **Comprobación Pendiente:**
  Verificar si existen pruebas de integración que asuman que un `PROVIDER` puede gestionar categorías o si es un requerimiento funcional formal no refinado.

---

### SEC-006: Logout vulnerable a CSRF y desincronización en resolución de tokens Bearer vs Cookies

- **Severidad:** MEDIA (CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:L/A:L — Base Score: 5.4)
- **Estado:** CONFIRMADO (Logout CSRF) / HIPÓTESIS (Desincronización de resolución)
- **Evidencia con Rutas y Líneas:**
  - [SecurityConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L53-L75):
    ```java
    53:         RequestMatcher bearerRequests = request -> {
    54:             String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    55:             return header != null && header.startsWith("Bearer ");
    56:         };
    ...
    73:                 .requireCsrfProtectionMatcher(cookieAuthenticatedUnsafeRequests)
    74:                 .ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/webhooks/**")
    75:                 .ignoringRequestMatchers(bearerRequests))
    ```
  - [JwtAuthenticationFilter.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java#L54-L58):
    ```java
    54:     private String resolveToken(HttpServletRequest request) {
    55:         return cookieService.resolve(request).orElseGet(() -> {
    56:             String header = request.getHeader("Authorization");
    57:             return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    58:         });
    59:     }
    ```
  - [application.yml](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/resources/application.yml#L128):
    `app.jwt.cookie.same-site: ${JWT_COOKIE_SAME_SITE:None}`
- **Escenario de Impacto:**
  1. **Logout CSRF Confirmado:** La ruta `/api/auth/logout` está explícitamente listada en `ignoringRequestMatchers`. Dado que la cookie de autenticación se emite con `SameSite=None; Secure`, un sitio web atacante puede incluir un formulario o script que realice un `POST /api/auth/logout` con `credentials: 'include'`. El navegador enviará la cookie automáticamente y el backend ejecutará el logout, provocando una denegación de servicio de sesión (forzar cierre de sesión no deseado) al usuario.
  2. **Riesgo por Desincronización Bearer vs Cookie:** Si un cliente envía una petición que contiene simultáneamente una cookie de sesión válida y una cabecera `Authorization: Bearer <token_invalido_o_vacio>`, la regla `bearerRequests.matches(request)` devuelve `true`, haciendo que el filtro CSRF ignore la petición. Sin embargo, en el filtro de autenticación `JwtAuthenticationFilter.resolveToken()`, la cookie se lee con mayor prioridad que el header. Si una petición lograse inducir ambas propiedades, el usuario se autenticaría vía cookie pero sin haber pasado la verificación de token CSRF.
- **Recomendación:**
  1. Proteger el endpoint `POST /api/auth/logout` con validación de token CSRF o implementarlo de manera que requiera la cabecera `X-XSRF-TOKEN` como el resto de peticiones mutantes.
  2. En `JwtAuthenticationFilter`, asegurar consistencia con `SecurityConfiguration`: si `Authorization: Bearer` está presente, no evaluar la cookie, o alternativamente evaluar CSRF siempre que viajen cookies independientemente de las cabeceras presentes.
- **Comprobación Pendiente:**
  Verificar en entorno de pruebas la respuesta de `POST /api/auth/logout` ante una petición de origen cruzado sin cabecera `X-XSRF-TOKEN`.

---

### SEC-007: Inconsistencia de autorización en catálogo: `GET /api/categories` protegido vs `GET /api/offerings` público

- **Severidad:** BAJA (CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L — Base Score: 3.7)
- **Estado:** CONFIRMADO
- **Evidencia con Rutas y Líneas:**
  - [SecurityConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L87-L90):
    ```java
    87:                 .requestMatchers(HttpMethod.GET, "/api/offerings").permitAll()
    88:                 .requestMatchers("/actuator/prometheus").permitAll()
    89:                 .requestMatchers("/error").permitAll()
    90:                 .anyRequest().authenticated())
    ```
  - [CategoryController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java#L38-L42):
    ```java
    38:     @GetMapping
    39:     @Operation(summary = "Lista las categorías activas")
    40:     public List<CategoryResponse> list() {
    41:         return listCategories.listActive().stream().map(CategoryResponse::from).toList();
    42:     }
    ```
- **Escenario de Impacto:**
  En la pantalla principal de la aplicación, el catálogo de ofertas (`GET /api/offerings`) es de acceso público anónimo. Sin embargo, el endpoint `GET /api/categories` no fue incluido en la lista de excepciones `permitAll()` de `SecurityConfiguration.java` y cae en `.anyRequest().authenticated()`. Si un usuario no autenticado intenta filtrar ofertas por categorías o la interfaz gráfica carga las categorías al inicio, la petición es rechazada con código HTTP `401 Unauthorized`.
- **Recomendación:**
  1. Agregar explícitamente `.requestMatchers(HttpMethod.GET, "/api/categories").permitAll()` en la configuración de `authorizeHttpRequests` de [SecurityConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L87).
- **Comprobación Pendiente:**
  Validar en los componentes Angular de la vista Home (`home.component.ts`) si se realiza una llamada a `CategoryService.list()` durante la carga pública no autenticada.

---

### SEC-008: Ausencia de mecanismo de revocación de tokens JWT en Logout y falta de invalidación inmediata de tokens activos

- **Severidad:** MEDIA (CVSS:3.1/AV:N/AC:H/PR:L/UI:N/S:U/C:H/I:L/A:N — Base Score: 5.8)
- **Estado:** CONFIRMADO
- **Evidencia con Rutas y Líneas:**
  - [AuthController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java#L46-L51):
    ```java
    46:     @PostMapping("/logout")
    47:     public ResponseEntity<Void> logout() {
    48:         return ResponseEntity.noContent()
    49:             .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
    50:             .build();
    51:     }
    ```
  - [JwtService.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java#L44-L48):
    ```java
    44:     public boolean isValid(String token, UserDetails userDetails) {
    45:         Claims claims = claims(token);
    46:         return claims.getSubject().equalsIgnoreCase(userDetails.getUsername())
    47:             && claims.getExpiration().after(new Date());
    48:     }
    ```
- **Escenario de Impacto:**
  El método `logout` únicamente solicita al navegador eliminar la cookie mediante la cabecera `Set-Cookie: access_token=; Max-Age=0`. El token JWT emitido mantiene su validez criptográfica durante 120 minutos (`app.jwt.expiration-minutes`). Si un token fue sustraído mediante herramientas de desarrollo, malware o interceptación previa, o si el cliente lo utiliza mediante la cabecera `Authorization: Bearer <token>`, el backend lo seguirá aceptando como válido durante dos horas, ya que no existe lista de revocación (Blacklist) en memoria o en Redis ni validación de versión o timestamp de emisión en la base de datos.
- **Recomendación:**
  1. Implementar un almacenamiento de tokens invalidados en Redis con TTL equivalente al tiempo remanente de expiración del token al momento del logout.
  2. Alternativamente, persistir un campo `tokenVersion` o `credentialsUpdatedAt` en la tabla `app_users` y contrastarlo en `JwtAuthenticationFilter` para invalidar de inmediato todos los tokens emitidos con anterioridad ante un logout o cambio de contraseña.
- **Comprobación Pendiente:**
  Verificar si Redis (ya configurado en `application.yml:74-76`) está disponible en todos los entornos para soportar la lista negra distribuida de tokens.

---

### SEC-009: Tránsito de datos de tarjeta de crédito en texto claro en capa de aplicación (`PaymentRequestDto`)

- **Severidad:** MEDIA (Riesgo de Cumplimiento / PCI-DSS Scope)
- **Estado:** CONFIRMADO
- **Evidencia con Rutas y Líneas:**
  - [PaymentRequestDto.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/dto/payment/PaymentRequestDto.java#L6-L12):
    ```java
    6: public record PaymentRequestDto(
    7:     UUID bookingId,
    8:     BigDecimal amount,
    9:     String cardNumber,
    10:     String expiryDate,
    11:     String cvv
    12: ) {}
    ```
  - [PaymentController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java#L26-L34):
    Recibe directamente el número de tarjeta, fecha de expiración y código CVV y los transmite al dominio a través de `PaymentCommand`.
- **Escenario de Impacto:**
  Aunque la arquitectura soporta integración con Stripe mediante `PaymentIntent` y Webhooks ([StripeWebhookController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookController.java)), la existencia de `PaymentRequestDto` procesando datos primarios de tarjetas (`PAN`, `CVV`) coloca a la aplicación en el nivel más estricto de cumplimiento normativo PCI-DSS (SAQ D). Si estos datos son volcados accidentalmente en logs de depuración, dumps de memoria o excepciones no controladas, se produce una vulneración de datos financieros sensibles.
- **Recomendación:**
  1. Migrar completamente la captura de tarjetas al frontend utilizando Stripe Elements o el SDK de cliente oficial de la pasarela, de modo que el backend de SkillBridge solo reciba y procese un `paymentMethodId` o `token` opaco generado directamente por la pasarela de pagos.
  2. Eliminar del DTO y del modelo de dominio los atributos `cardNumber`, `expiryDate` y `cvv`.
- **Comprobación Pendiente:**
  Constatar si el frontend Angular en `features/payment-modal/` consume `PaymentController` enviando datos simulados o si interactúa directamente con el SDK de Stripe.

---

### SEC-010: Ausencia de limitación de tasa (Rate Limiting) en autenticación y llamadas a IA

- **Severidad:** MEDIA (CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L — Base Score: 5.3)
- **Estado:** CONFIRMADO
- **Evidencia con Rutas y Líneas:**
  - [SecurityConfiguration.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L82-L90):
    No contiene filtros de limitación de frecuencia (Rate Limiting o Throttling) en la cadena de filtros de Spring Security.
  - [AuthController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AuthController.java#L40-L44):
    No implementa conteo de intentos fallidos ni bloqueo temporal por IP o cuenta.
  - [AiController.java](file:///home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java#L48-L72):
    Permite llamadas concurrentes ilimitadas a APIs externas de pago (`/recommendations`, `/recommendations/voice`, `/recommendations/image`, `/tts`).
- **Escenario de Impacto:**
  1. Permite ataques de fuerza bruta continuos contra `/api/auth/login` sin disparar ningún mecanismo de defensa en el backend.
  2. Un usuario autenticado con intenciones maliciosas o un script automatizado puede saturar los endpoints de IA, agotando las cuotas mensuales de las APIs de Google Gemini, Groq o ElevenLabs, o incurriendo en costos no presupuestados (Denial of Wallet).
- **Recomendación:**
  1. Implementar un filtro de Rate Limiting en Spring Security (utilizando librerías como Bucket4j respaldado por Redis).
  2. Aplicar cuotas estrictas para llamadas anónimas a endpoints de autenticación (ej. 5 intentos de login por minuto por IP) y para llamadas de usuarios a los servicios de IA generativa (ej. 10 peticiones de recomendación por usuario por hora).
- **Comprobación Pendiente:**
  Verificar si en el entorno de producción existe un Web Application Firewall (WAF) perimetral (ej. Cloudflare o AWS WAF) que aplique mitigación de fuerza bruta antes de alcanzar el servidor Spring Boot.

---

## 9. Limitaciones de la Auditoría y Áreas No Verificadas

1. **Secretos en Variables de Entorno de Producción:**  
   No es posible constatar en el código estático si las variables de entorno `JWT_SECRET`, `STRIPE_WEBHOOK_SECRET`, `GEMINI_API_KEY` u otras son configuradas con valores seguros en la plataforma de despliegue real (Render / AWS / GCP).
2. **Topología de Red Perimetral y Terminación TLS:**  
   La terminación de certificados SSL/TLS y la configuración de cabeceras de proxy (`X-Forwarded-Proto`, `server.forward-headers-strategy: framework`) dependen del reverse proxy o API Gateway desplegado frente al contenedor de Spring Boot.
3. **Persistencia Real de `audit-service`:**  
   No se evaluó la configuración de autenticación interna de MongoDB para `audit-service` al tratarse de infraestructura externa al alcance de código de Spring Boot.

---

**Fin del Informe de Auditoría de Seguridad — SkillBridge AI**  
*Documento emitido conforme a las políticas de aseguramiento técnico y revisión de código estático.*
