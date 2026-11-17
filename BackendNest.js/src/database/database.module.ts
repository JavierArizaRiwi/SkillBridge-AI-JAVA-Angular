import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ConfigService } from '@nestjs/config';
import { databaseEntities, databaseMigrations } from './data-source.js';

@Module({
  imports: [
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        type: 'postgres' as const,
        host: configService.get<string>('DB_HOST') ?? 'localhost',
        port: configService.get<number>('DB_PORT', 5432),
        username: configService.get<string>('DB_USERNAME'),
        password: configService.get<string>('DB_PASSWORD'),
        database: configService.get<string>('DB_DATABASE'),
        entities: databaseEntities,
        migrations: databaseMigrations,
        migrationsRun: true,
        autoLoadEntities: true,
        synchronize: false,
      }),
    }),
  ],
})
export class DatabaseModule {}
