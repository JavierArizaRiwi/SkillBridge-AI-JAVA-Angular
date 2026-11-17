import {
  PostgreSqlContainer,
  type StartedPostgreSqlContainer,
} from '@testcontainers/postgresql';
import { randomUUID } from 'node:crypto';
import { DataSource } from 'typeorm';
import { TypeOrmLiveRoomRepository } from '../src/mentorship/live-room/adapters/typeorm-live-room.repository.js';
import {
  LiveProvider,
  LiveRoomSyncState,
  MentorshipMode,
  MentorshipStatus,
} from '../src/mentorship/live-room/domain/live-room.types.js';
import { LiveRoomEntity } from '../src/mentorship/entities/live-room.entity.js';
import { MentorshipAssignmentEntity } from '../src/mentorship/entities/mentorship-assignment.entity.js';
import { OutboxEventEntity } from '../src/mentorship/entities/outbox-event.entity.js';
import {
  buildDataSourceOptions,
  databaseEntities,
  databaseMigrations,
} from '../src/database/data-source.js';

const runDbTests = process.env.RUN_DB_TESTS === '1';
const describeDb = runDbTests ? describe : describe.skip;

describeDb('Persistencia PostgreSQL (Testcontainers)', () => {
  let container: StartedPostgreSqlContainer;
  let dataSource: DataSource;
  let repository: TypeOrmLiveRoomRepository;
  let assignmentId: string;

  beforeAll(async () => {
    container = await new PostgreSqlContainer('postgres:17-alpine').start();
    dataSource = new DataSource(
      buildDataSourceOptions({
        host: container.getHost(),
        port: container.getPort(),
        username: container.getUsername(),
        password: container.getPassword(),
        database: container.getDatabase(),
        entities: databaseEntities,
        migrations: databaseMigrations,
      }),
    );
    await dataSource.initialize();
    await dataSource.runMigrations();
    repository = new TypeOrmLiveRoomRepository(dataSource);
  }, 120_000);

  afterAll(async () => {
    await dataSource?.destroy();
    await container?.stop();
  });

  beforeEach(async () => {
    await dataSource.getRepository(OutboxEventEntity).clear();
    await dataSource.getRepository(LiveRoomEntity).clear();
    await dataSource.getRepository(MentorshipAssignmentEntity).clear();

    assignmentId = randomUUID();
    await dataSource.getRepository(MentorshipAssignmentEntity).insert({
      id: assignmentId,
      bookingId: randomUUID(),
      customerId: 'customer-1',
      providerId: 'provider-1',
      offeringId: 'offering-1',
      mode: MentorshipMode.LIVE,
      status: MentorshipStatus.SCHEDULED,
      accessStartsAt: new Date(Date.now() - 60_000),
      accessEndsAt: new Date(Date.now() + 3_600_000),
      progressPercent: 0,
      completedAt: null,
      version: 0,
    });
  });

  it('aplica la migración y crea las tablas', async () => {
    const tables = await dataSource.query<{ table_name: string }[]>(
      `SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'`,
    );
    const names = tables.map((t) => t.table_name);
    expect(names).toEqual(
      expect.arrayContaining([
        'mentorship_assignments',
        'live_rooms',
        'outbox_events',
      ]),
    );
  });

  it('serializa 10 solicitudes concurrentes en una sola sala y un solo evento', async () => {
    const results = await Promise.all(
      Array.from({ length: 10 }, () =>
        repository.runLocked(assignmentId, async (tx) => {
          const existing = await tx.findRoom(assignmentId);
          if (existing) {
            return false;
          }
          await tx.saveRoom({
            id: randomUUID(),
            assignmentId,
            provider: LiveProvider.ZOOM,
            externalRoomId: '999',
            joinUrl: 'https://zoom.example/j/999',
            hostUrl: 'https://zoom.example/s/999',
            passcodeEnc: null,
            hostSignatureEnc: null,
            inviteUrlEnc: null,
            syncState: LiveRoomSyncState.SYNCED,
          });
          await tx.saveOutbox({
            eventId: randomUUID(),
            eventType: 'MentorshipLiveRoomCreated',
            aggregateType: 'Mentorship',
            aggregateId: assignmentId,
            version: 1,
            correlationId: null,
            occurredAt: new Date(),
            payload: { assignmentId, provider: 'ZOOM', fallbackUsed: false },
          });
          return true;
        }),
      ),
    );

    expect(results.filter(Boolean)).toHaveLength(1);
    expect(await dataSource.getRepository(LiveRoomEntity).count()).toBe(1);
    expect(await dataSource.getRepository(OutboxEventEntity).count()).toBe(1);
  });

  it('revierte la sala si el outbox falla en la misma transacción', async () => {
    await expect(
      repository.runLocked(assignmentId, async (tx) => {
        await tx.saveRoom({
          id: randomUUID(),
          assignmentId,
          provider: LiveProvider.ZOOM,
          externalRoomId: '999',
          joinUrl: 'https://zoom.example/j/999',
          hostUrl: null,
          passcodeEnc: null,
          hostSignatureEnc: null,
          inviteUrlEnc: null,
          syncState: LiveRoomSyncState.SYNCED,
        });
        throw new Error('outbox caído');
      }),
    ).rejects.toThrow('outbox caído');

    expect(await dataSource.getRepository(LiveRoomEntity).count()).toBe(0);
  });
});
