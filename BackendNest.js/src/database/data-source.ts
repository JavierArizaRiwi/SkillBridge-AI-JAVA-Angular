import { DataSource, type DataSourceOptions } from 'typeorm';
import { MentorshipAssignmentEntity } from '../mentorship/entities/mentorship-assignment.entity.js';
import { LiveRoomEntity } from '../mentorship/entities/live-room.entity.js';
import { OutboxEventEntity } from '../mentorship/entities/outbox-event.entity.js';
import { InitialSchema1700000000000 } from './migrations/1700000000000-InitialSchema.js';

export const databaseEntities = [
  MentorshipAssignmentEntity,
  LiveRoomEntity,
  OutboxEventEntity,
];

export const databaseMigrations = [InitialSchema1700000000000];

export function buildDataSourceOptions(
  overrides: Partial<DataSourceOptions> = {},
): DataSourceOptions {
  return {
    type: 'postgres',
    host: process.env.DB_HOST ?? 'localhost',
    port: Number(process.env.DB_PORT ?? 5432),
    username: process.env.DB_USERNAME ?? 'postgres',
    password: process.env.DB_PASSWORD ?? 'postgres',
    database: process.env.DB_DATABASE ?? 'skillbridge_mentorship',
    entities: databaseEntities,
    migrations: databaseMigrations,
    synchronize: false,
    ...overrides,
  } as DataSourceOptions;
}

export function createDataSource(
  overrides: Partial<DataSourceOptions> = {},
): DataSource {
  return new DataSource(buildDataSourceOptions(overrides));
}
