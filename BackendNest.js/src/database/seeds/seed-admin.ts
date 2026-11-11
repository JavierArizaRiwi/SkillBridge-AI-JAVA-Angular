import 'reflect-metadata';
import { Logger } from '@nestjs/common';
import * as z from 'zod';
import dataSource from '../data-source.js';
import { User } from '../../modules/users/entities/user.entity.js';
import { UserRole, UserStatus } from '../../modules/users/enums/index.js';
import { PasswordService } from '../../modules/users/password.service.js';

const logger = new Logger('SeedAdmin');

// Mismas reglas que el registro (D-002): email válido y contraseña de 8–128
// caracteres con al menos una letra y un número.
const adminEnvSchema = z.object({
  ADMIN_EMAIL: z.email({ error: 'ADMIN_EMAIL debe ser un email válido' }),
  ADMIN_PASSWORD: z
    .string({ error: 'ADMIN_PASSWORD es obligatorio' })
    .min(8, 'ADMIN_PASSWORD debe tener al menos 8 caracteres')
    .max(128)
    .regex(
      /^(?=.*\p{L})(?=.*\p{N}).*$/u,
      'ADMIN_PASSWORD necesita una letra y un número',
    ),
  ADMIN_FIRST_NAME: z.string().min(1).default('Administrador'),
  ADMIN_LAST_NAME: z.string().min(1).default('ZeroBug'),
  ADMIN_PHONE: z.string().min(1).default('0000000000'),
});

/**
 * Crea el primer ADMIN (HU-017, #84): sin él nadie puede dar de alta
 * empleados. Es idempotente: si ya existe una cuenta con ese email no la toca
 * (ni la contraseña ni el rol), así que se puede ejecutar las veces que haga falta.
 *
 * Uso: ADMIN_EMAIL=admin@zerobug.dev ADMIN_PASSWORD='Cambia1234' npm run seed:admin
 */
async function seedAdmin(): Promise<void> {
  const parsed = adminEnvSchema.safeParse(process.env);
  if (!parsed.success) {
    const details = parsed.error.issues
      .map((issue) => `  - ${issue.path.join('.')}: ${issue.message}`)
      .join('\n');
    throw new Error(`Variables del administrador inválidas:\n${details}`);
  }
  const env = parsed.data;
  const email = env.ADMIN_EMAIL.trim().toLowerCase();

  await dataSource.initialize();
  try {
    const users = dataSource.getRepository(User);

    const existing = await users.findOneBy({ email });
    if (existing) {
      logger.log(
        `Ya existe ${email} (rol ${existing.role}); no se modifica nada`,
      );
      return;
    }

    await users.save(
      users.create({
        firstName: env.ADMIN_FIRST_NAME,
        lastName: env.ADMIN_LAST_NAME,
        email,
        phone: env.ADMIN_PHONE,
        passwordHash: await new PasswordService().hash(env.ADMIN_PASSWORD),
        role: UserRole.ADMIN,
        status: UserStatus.ACTIVE,
      }),
    );
    logger.log(`Administrador creado: ${email}`);
  } finally {
    await dataSource.destroy();
  }
}

seedAdmin().catch((error: unknown) => {
  logger.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
