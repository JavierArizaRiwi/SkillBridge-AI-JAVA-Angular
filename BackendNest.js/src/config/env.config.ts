export const EnvConfig = () => ({
  app: {
    port: Number(process.env.APP_PORT),
    env: process.env.NODE_ENV,
    corsOrigins: (process.env.CORS_ORIGIN ?? '*')
      .split(',')
      .map((origin) => origin.trim())
      .filter(Boolean),
  },
  database: {
    host: process.env.DATABASE_HOST,
    port: Number(process.env.DATABASE_PORT),
    user: process.env.DATABASE_USER,
    password: process.env.DATABASE_PASSWORD,
    name: process.env.DATABASE_NAME,
  },
  reservations: {
    durationMinutes: Number(process.env.RESERVATION_DURATION_MINUTES ?? 120),
    noShowToleranceMinutes: Number(
      process.env.RESERVATION_NO_SHOW_TOLERANCE_MINUTES ?? 15,
    ),
  },
  jwt: {
    secret: process.env.JWT_SECRET,
    expiresIn: process.env.JWT_EXPIRES_IN ?? '1h',
  },
  passwordReset: {
    ttlMinutes: Number(process.env.PASSWORD_RESET_TTL_MINUTES ?? 30),
    url:
      process.env.PASSWORD_RESET_URL ?? 'http://localhost:3000/reset-password',
  },
  observe: {
    appKey: process.env.OBSERVE_APP_KEY,
    appSecret: process.env.OBSERVE_APP_SECRET,
    serviceId: process.env.OBSERVE_SERVICE_ID,
  },
});
