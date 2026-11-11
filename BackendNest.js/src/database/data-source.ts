import 'reflect-metadata';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DataSource } from 'typeorm';
import { envValidationSchema } from '../config/env.validation.schema.js';

// DataSource usado únicamente por la CLI de TypeORM (npm run migration:*).
// La aplicación NestJS se conecta a través de database.config.ts; ambos leen
// las mismas variables DATABASE_* para que no diverjan.

// En local las variables vienen de .env; dentro de Docker ya están en el entorno.
try {
  process.loadEnvFile('.env');
} catch {
  // sin .env: se asume que las variables ya existen en el entorno
}

const env = envValidationSchema.parse(process.env);
const here = dirname(fileURLToPath(import.meta.url));

export default new DataSource({
  type: 'postgres',
  host: env.DATABASE_HOST,
  port: env.DATABASE_PORT,
  username: env.DATABASE_USER,
  password: env.DATABASE_PASSWORD,
  database: env.DATABASE_NAME,
  entities: [join(here, '..', '**', '*.entity.{ts,js}')],
  migrations: [join(here, 'migrations', '*.{ts,js}')],
  migrationsTableName: 'migrations',
  synchronize: false,
});
