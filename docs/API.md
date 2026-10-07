# API starter

Base path: `/api`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register CUSTOMER and return JWT |
| POST | `/auth/login` | Public | Login and return JWT |
| GET | `/offerings` | Public | Active catalog; Redis-backed |
| POST | `/bookings` | Bearer JWT | Persist booking and publish event |
| POST | `/ai/recommendations` | Bearer JWT | Generate catalog-grounded recommendation |


OpenAPI UI locally:

```text
http://localhost:8080/swagger-ui.html
```

## Suggested next endpoints

```text
GET    /api/offerings                     público, catálogo activo
GET    /api/offerings/me                  PROVIDER
POST   /api/offerings                     PROVIDER, ADMIN
PUT    /api/offerings/{id}                PROVIDER (dueño), ADMIN
POST   /api/offerings/{id}/deactivate     PROVIDER (dueño), ADMIN
POST   /api/offerings/{id}/activate       PROVIDER (dueño), ADMIN
GET    /api/admin/offerings               ADMIN
```

The provider write endpoints should invalidate the public offerings cache through `OfferingCachePort`.
