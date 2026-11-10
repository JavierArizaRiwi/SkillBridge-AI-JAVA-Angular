# Deployment Checklist

> For a step-by-step procedural tutorial on provisioning and deploying to free cloud providers (Neon, Upstash, CloudAMQP, Render, Vercel), see the [Free Tier Deployment Guide](guides/free-tier-deployment.md).

## Before deploying
- [ ] Tests pass.
- [ ] No secrets are committed.
- [ ] `JWT_PRIVATE_KEY` is set (RSA >= 2048 bits, PKCS#8 PEM) and is **not** committed. The backend refuses to start without it.
- [ ] `JWT_GENERATE_EPHEMERAL_KEYS` is `false` (or unset). It is for local development only: sessions are lost on every restart.
- [ ] Migrating from HS256: `JWT_LEGACY_SECRET` is set only until the old tokens expire (120 min by default), then removed.
- [ ] Services that validate tokens (`mentorship-service`, `audit-service`) receive only `JWT_PUBLIC_KEY`, never the private key.
- [ ] Production database uses TLS.
- [ ] Redis URL uses TLS when the provider requires it.
- [ ] RabbitMQ cloud URL uses the credentials supplied by the provider.
- [ ] `GEMINI_API_KEY` exists only in backend environment variables.
- [ ] `CORS_ALLOWED_ORIGINS` contains only the real frontend URL(s).
- [ ] `/actuator/health` returns UP.
- [ ] Swagger is reviewed; consider disabling it for a real production product.

## After deploying
- [ ] Register/login works.
- [ ] Public catalog works twice (second request should be cache-eligible).
- [ ] Authenticated AI endpoint works.
- [ ] Create booking works.
- [ ] RabbitMQ queue is consumed.
- [ ] Logs contain no passwords/tokens/API keys.
- [ ] Metrics are visible locally with the observability profile.
