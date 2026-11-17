import { ConfigService } from '@nestjs/config';
import { SecretCipher } from '../../src/common/secret-cipher.js';
import { LiveRoomMetrics } from '../../src/mentorship/live-room/live-room.metrics.js';
import {
  LiveProvider,
  LiveRoomSyncState,
  MentorshipMode,
  MentorshipStatus,
  type AssignmentSnapshot,
  type LiveRoomRecord,
  type OutboxRecord,
  type ProviderRoomResult,
} from '../../src/mentorship/live-room/domain/live-room.types.js';
import { LiveRoomService } from '../../src/mentorship/live-room/live-room.service.js';
import type {
  CreateRoomInput,
  LiveMeetingPort,
} from '../../src/mentorship/live-room/ports/live-meeting.port.js';
import type {
  LiveRoomRepository,
  LiveRoomTransaction,
} from '../../src/mentorship/live-room/ports/live-room.repository.js';

export function createAssignment(
  overrides: Partial<AssignmentSnapshot> = {},
): AssignmentSnapshot {
  const start = overrides.accessStartsAt ?? new Date(Date.now() - 60_000);
  const end = overrides.accessEndsAt ?? new Date(Date.now() + 3_600_000);
  return {
    id: overrides.id ?? '11111111-1111-4111-8111-111111111111',
    bookingId: overrides.bookingId ?? '22222222-2222-4222-8222-222222222222',
    customerId: overrides.customerId ?? 'customer-1',
    providerId: overrides.providerId ?? 'provider-1',
    offeringId: overrides.offeringId ?? 'offering-1',
    mode: overrides.mode ?? MentorshipMode.LIVE,
    status: overrides.status ?? MentorshipStatus.SCHEDULED,
    accessStartsAt: start,
    accessEndsAt: end,
  };
}

export function createCipher(): SecretCipher {
  const config = {
    get: (key: string) =>
      key === 'liveRoom.encryptionKey' ? 'test-encryption-key' : undefined,
  } as unknown as ConfigService;
  return new SecretCipher(config);
}

export class FakeLiveRoomRepository implements LiveRoomRepository {
  readonly assignments = new Map<string, AssignmentSnapshot>();
  readonly rooms = new Map<string, LiveRoomRecord>();
  readonly outbox: OutboxRecord[] = [];
  failNextSave = false;

  private tail: Promise<unknown> = Promise.resolve();

  addAssignment(assignment: AssignmentSnapshot): void {
    this.assignments.set(assignment.id, assignment);
  }

  seedRoom(room: LiveRoomRecord): void {
    this.rooms.set(room.assignmentId, room);
  }

  async findAssignment(assignmentId: string): Promise<AssignmentSnapshot | null> {
    return this.assignments.get(assignmentId) ?? null;
  }

  async findRoomByAssignment(assignmentId: string): Promise<LiveRoomRecord | null> {
    return this.rooms.get(assignmentId) ?? null;
  }

  runLocked<T>(
    _assignmentId: string,
    work: (tx: LiveRoomTransaction) => Promise<T>,
  ): Promise<T> {
    const run = this.tail.then(() => work(this.transaction()));
    this.tail = run.then(
      () => undefined,
      () => undefined,
    );
    return run;
  }

  private transaction(): LiveRoomTransaction {
    return {
      findRoom: async (assignmentId) => this.rooms.get(assignmentId) ?? null,
      saveRoom: async (room) => {
        if (this.failNextSave) {
          this.failNextSave = false;
          throw new Error('fallo simulado al persistir la sala');
        }
        const now = new Date();
        const record: LiveRoomRecord = {
          ...room,
          createdAt: now,
          updatedAt: now,
        };
        this.rooms.set(room.assignmentId, record);
        return record;
      },
      saveOutbox: async (event) => {
        this.outbox.push(event);
      },
    };
  }
}

export class FakeLiveMeetingAdapter implements LiveMeetingPort {
  calls = 0;

  constructor(
    readonly provider: LiveProvider,
    private readonly result: ProviderRoomResult,
    private readonly failWith?: () => Error,
  ) {}

  async createRoom(_input: CreateRoomInput): Promise<ProviderRoomResult> {
    this.calls += 1;
    if (this.failWith) {
      throw this.failWith();
    }
    return this.result;
  }

  async findExistingRoom(): Promise<ProviderRoomResult | null> {
    return null;
  }
}

export function createService(
  repository: LiveRoomRepository,
  adapters: Map<LiveProvider, LiveMeetingPort>,
  metrics: LiveRoomMetrics = new LiveRoomMetrics(),
): LiveRoomService {
  return new LiveRoomService(repository, adapters, createCipher(), metrics);
}

export function roomRecord(
  overrides: Partial<LiveRoomRecord> = {},
): LiveRoomRecord {
  const now = new Date();
  return {
    id: overrides.id ?? 'room-1',
    assignmentId: overrides.assignmentId ?? 'assignment-1',
    provider: overrides.provider ?? LiveProvider.ZOOM,
    externalRoomId: overrides.externalRoomId ?? '999',
    joinUrl: overrides.joinUrl ?? 'https://zoom.example/j/999',
    hostUrl: overrides.hostUrl ?? 'https://zoom.example/s/999',
    passcodeEnc: overrides.passcodeEnc ?? null,
    hostSignatureEnc: overrides.hostSignatureEnc ?? null,
    inviteUrlEnc: overrides.inviteUrlEnc ?? null,
    syncState: overrides.syncState ?? LiveRoomSyncState.SYNCED,
    createdAt: overrides.createdAt ?? now,
    updatedAt: overrides.updatedAt ?? now,
  };
}
