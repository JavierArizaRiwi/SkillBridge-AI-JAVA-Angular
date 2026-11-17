import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { configuration } from './configuration.js';

/**
 * Configuración global del servicio. Se registra una sola vez y queda
 * disponible para todos los módulos mediante `ConfigService`.
 */
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      cache: true,
      load: [configuration],
    }),
  ],
})
export class AppConfigModule {}
