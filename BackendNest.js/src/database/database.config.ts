import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ConfigService } from '@nestjs/config';
import { TypeOrmModuleOptions } from '@nestjs/typeorm';

const here = dirname(fileURLToPath(import.meta.url));

export const databaseConfiguration = (
  config: ConfigService,
): TypeOrmModuleOptions => {
  return {
    type: 'postgres',
    host: config.getOrThrow<string>('database.host'),
    port: config.getOrThrow<number>('database.port'),
    username: config.getOrThrow<string>('database.user'),
    password: config.getOrThrow<string>('database.password'),
    database: config.getOrThrow<string>('database.name'),
    autoLoadEntities: true,
    synchronize: false,
    logging: config.get<string>('app.env') === 'development',
    // En dev resuelve a src/database/migrations/*.ts; en el build a dist/database/migrations/*.js
    migrations: [join(here, 'migrations', '*.{ts,js}')],
    // Decisión: las migraciones pendientes se aplican al arrancar la API
    // (también dentro de Docker), así el entorno queda listo con `docker compose up`.
    migrationsRun: true,
    migrationsTableName: 'migrations',
    migrationsTransactionMode: 'all',
  };
};
