# Mentorías — seguridad e identidad (RS256)

## Decisión

El monolito emite JWT firmados con **RS256**. Los servicios satélite validan con la clave pública.
Antes: HS256 con secreto compartido (`app.jwt.secret` / `JWT_SECRET`, `JwtService`), lo que obligaba a
entregar el secreto de firma a cualquier servicio que validara tokens.

## Claves

| Variable | Dónde | Contenido |
|---|---|---|
| `JWT_PRIVATE_KEY` | Solo monolito | PEM PKCS#8 (RSA ≥ 2048). **Sin valor por defecto**: si falta, el monolito no arranca |
| `JWT_PUBLIC_KEY` | `mentorship-service`, `audit-service` (y opcional en el monolito, que la deriva de la privada) | PEM X.509 |
| `JWT_KEY_ID` | Monolito | `kid` de la cabecera (por defecto `skillbridge-1`) |
| `JWT_LEGACY_SECRET` | Solo monolito, solo durante la migración | Valida (nunca emite) tokens HS256 antiguos |
| `JWT_GENERATE_EPHEMERAL_KEYS` | Solo desarrollo | Par en memoria; `false` por defecto. `docker-compose.yml` lo activa para uso local |

- El PEM puede ir en una línea con secuencias `\n` literales (variables de entorno).
- Nunca se versionan claves reales; `.env.example` documenta solo los nombres.
- Esto resuelve SEC-002 (secreto JWT por defecto en `application.yml`): ya no existe ninguna clave por defecto.
- Rotación: **pendiente**. Hoy hay una sola clave pública por servicio; el `kid` ya viaja en la cabecera
  para soportar una lista de claves por `kid` más adelante.

## Claims (implementado)

| Claim | Valor | Nota |
|---|---|---|
| `sub` | **correo** del usuario | Se mantiene para no romper el filtro del monolito |
| `uid` | UUID del usuario | **Es el identificador que deben usar los servicios** (p. ej. `customerId`) |
| `email` | correo | Igual que `sub` |
| `role` | `CUSTOMER` \| `PROVIDER` \| `ADMIN` | |
| `iss` | `skillbridge` | Obligatorio en RS256 |
| `iat`, `exp` | epoch | `exp` = `JWT_EXPIRATION_MINUTES` (120 por defecto) |

Cabecera: `alg = RS256`, `kid`. Los servicios rechazan cualquier `alg` distinto de `RS256`.
Los tokens HS256 antiguos no llevan `uid` ni `iss`: solo el monolito, con `JWT_LEGACY_SECRET`, los acepta.

## Compatibilidad durante la migración

1. Despliegue en dos fases: el monolito acepta HS256 y RS256 durante una ventana igual a la vida
   máxima del token; emite solo RS256.
2. Tras la ventana se vacía `JWT_LEGACY_SECRET` y se retira el soporte HS256.
3. Los tokens HS256 ya emitidos fuerzan un nuevo login al expirar; no se migran.

## Pendiente de verificar

- **[UNKNOWN]** Si `audit-service` valida JWT hoy y con qué mecanismo.
- **[UNKNOWN]** Si hay otras piezas que lean `app.jwt.secret` (p. ej. tests o el frontend).
- La clave del SDK de Zoom (`ZoomMeetingClient`) usa HS256 por requisito de Zoom; es independiente y no se toca.
