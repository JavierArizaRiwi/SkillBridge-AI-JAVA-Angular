import * as z from 'zod';

// Fuente única de verdad de las variables de entorno.
// Lo usan ConfigModule (app.module.ts, opción `validate`) y la CLI de TypeORM (data-source.ts).
// Una variable obligatoria ausente o inválida detiene el arranque (RN-002, RN-013).
export const envValidationSchema = z.object({
  NODE_ENV: z
    .enum(['development', 'production', 'test'])
    .default('development'),
  APP_PORT: z.coerce.number().int().positive().default(3000),

  DATABASE_HOST: z.string().min(1),
  DATABASE_PORT: z.coerce.number().int().positive().default(5432),
  DATABASE_USER: z.string().min(1),
  DATABASE_PASSWORD: z.string().min(1),
  DATABASE_NAME: z.string().min(1),

  // Orígenes permitidos por CORS, separados por coma. "*" solo fuera de producción.
  CORS_ORIGIN: z.string().min(1).default('*'),

  // Reglas de reservas (decisión D-001 en docs/decisiones.md).
  // Duración que ocupa una reserva en la mesa: define el conflicto horario (RN-040, RN-045).
  RESERVATION_DURATION_MINUTES: z.coerce
    .number()
    .int()
    .min(15)
    .max(720)
    .default(120),
  // Minutos tras la hora reservada antes de poder marcarla como NO_SHOW (RN-078).
  RESERVATION_NO_SHOW_TOLERANCE_MINUTES: z.coerce
    .number()
    .int()
    .min(0)
    .max(120)
    .default(15),

  // Firma de los access tokens (RN-092). Obligatorio: sin él la app no arranca.
  JWT_SECRET: z
    .string({ error: 'JWT_SECRET es obligatorio (mínimo 32 caracteres)' })
    .min(32, 'JWT_SECRET debe tener al menos 32 caracteres'),
  // Vida del access token: número de segundos o con unidad (15m, 1h, 7d)
  JWT_EXPIRES_IN: z
    .string()
    .regex(/^\d+[smhd]?$/, 'JWT_EXPIRES_IN debe ser segundos o 15m, 1h, 7d…')
    .default('1h'),

  // Recuperación de contraseña (HU-018): vida del token y enlace que se envía
  PASSWORD_RESET_TTL_MINUTES: z.coerce
    .number()
    .int()
    .min(5)
    .max(1440)
    .default(30),
  PASSWORD_RESET_URL: z.url().default('http://localhost:3000/reset-password'),

  OBSERVE_APP_KEY: z.string().optional(),
  OBSERVE_APP_SECRET: z.string().optional(),
  OBSERVE_SERVICE_ID: z.string().optional(),
});

export type Env = z.infer<typeof envValidationSchema>;

const schemaWithRules = envValidationSchema.superRefine((env, ctx) => {
  if (
    env.NODE_ENV === 'production' &&
    env.CORS_ORIGIN.split(',').some((o) => o.trim() === '*')
  ) {
    ctx.addIssue({
      code: 'custom',
      path: ['CORS_ORIGIN'],
      message:
        'En producción CORS_ORIGIN debe listar orígenes explícitos; "*" no está permitido (RN-002)',
    });
  }
});

export const validateEnv = (config: Record<string, unknown>): Env => {
  const result = schemaWithRules.safeParse(config);

  if (!result.success) {
    const details = result.error.issues
      .map((issue) => `  - ${issue.path.join('.')}: ${issue.message}`)
      .join('\n');

    throw new Error(`Variables de entorno inválidas:\n${details}`);
  }

  return result.data;
};
