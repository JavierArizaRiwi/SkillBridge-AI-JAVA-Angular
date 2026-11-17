import { Injectable } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { randomUUID } from 'node:crypto';
import { type DataSource, type EntityManager } from 'typeorm';
import { LiveRoomEntity } from '../../entities/live-room.entity.js';
import { MentorshipAssignmentEntity } from '../../entities/mentorship-assignment.entity.js';
import { OutboxEventEntity } from '../../entities/outbox-event.entity.js';
import type {
  AssignmentSnapshot,
  LiveRoomRecord,
  OutboxRecord,
} from '../domain/live-room.types.js';
import type {
  LiveRoomRepository,
  LiveRoomTransaction,
  NewLiveRoom,
} from '../ports/live-room.repository.js';

@Injectable()
export class TypeOrmLiveRoomRepository implements LiveRoomRepository {
  constructor(@InjectDataSource() private readonly dataSource: DataSource) {}

  async findAssignment(assignmentId: string): Promise<AssignmentSnapshot | null> {
    const row = await this.dataSource
      .getRepository(MentorshipAssignmentEntity)
      .findOne({ where: { id: assignmentId } });
    return row ? mapAssignment(row) : null;
  }

  async findRoomByAssignment(assignmentId: string): Promise<LiveRoomRecord | null> {
    const row = await this.dataSource
      .getRepository(LiveRoomEntity)
      .findOne({ where: { assignmentId } });
    return row ? mapRoom(row) : null;
  }

  async runLocked<T>(
    assignmentId: string,
    work: (tx: LiveRoomTransaction) => Promise<T>,
  ): Promise<T> {
    return this.dataSource.transaction(async (manager) => {
      // Bloqueo asesor por asignación: serializa la creación y garantiza una
      // sola llamada externa (FR-015) dentro de la misma transacción (FR-017).
      await manager.query(`SET LOCAL lock_timeout = '5000ms'`);
      await manager.query('SELECT pg_advisory_xact_lock(hashtextextended($1, 0))', [
        assignmentId,
      ]);
      return work(this.createTransaction(manager));
    });
  }

  private createTransaction(manager: EntityManager): LiveRoomTransaction {
    return {
      findRoom: async (assignmentId) => {
        const row = await manager
          .getRepository(LiveRoomEntity)
          .findOne({ where: { assignmentId } });
        return row ? mapRoom(row) : null;
      },
      saveRoom: async (room: NewLiveRoom) => {
        const repository = manager.getRepository(LiveRoomEntity);
        const entity = repository.create(room);
        const saved = await repository.save(entity);
        return mapRoom(saved);
      },
      saveOutbox: async (event: OutboxRecord) => {
        const repository = manager.getRepository(OutboxEventEntity);
        const entity = repository.create({ id: randomUUID(), ...event, publishedAt: null });
        await repository.save(entity);
      },
    };
  }
}

function mapAssignment(row: MentorshipAssignmentEntity): AssignmentSnapshot {
  return {
    id: row.id,
    bookingId: row.bookingId,
    customerId: row.customerId,
    providerId: row.providerId,
    offeringId: row.offeringId,
    mode: row.mode,
    status: row.status,
    accessStartsAt: toDate(row.accessStartsAt),
    accessEndsAt: toDate(row.accessEndsAt),
  };
}

function mapRoom(row: LiveRoomEntity): LiveRoomRecord {
  return {
    id: row.id,
    assignmentId: row.assignmentId,
    provider: row.provider,
    externalRoomId: row.externalRoomId,
    joinUrl: row.joinUrl,
    hostUrl: row.hostUrl,
    passcodeEnc: row.passcodeEnc,
    hostSignatureEnc: row.hostSignatureEnc,
    inviteUrlEnc: row.inviteUrlEnc,
    syncState: row.syncState,
    createdAt: toDate(row.createdAt),
    updatedAt: toDate(row.updatedAt),
  };
}

function toDate(value: Date | string): Date {
  return value instanceof Date ? value : new Date(value);
}
