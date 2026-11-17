import { Inject, Injectable } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import type { AuthenticatedUser } from '../../auth/auth.types.js';
import { DomainError, DomainErrors } from '../../common/domain-error.js';
import { SecretCipher } from '../../common/secret-cipher.js';
import {
  LiveProvider,
  LiveRoomSyncState,
  type AssignmentSnapshot,
  type HostAccess,
  type LiveRoomRecord,
  type ParticipantAccess,
  type OutboxRecord,
  canCreateLiveRoom,
  isLiveMode,
  isSupportedProvider,
  isTerminalStatus,
  isWithinAccessWindow,
} from './domain/live-room.types.js';
import { LiveRoomMetrics } from './live-room.metrics.js';
import type { LiveMeetingPort } from './ports/live-meeting.port.js';
import { LIVE_MEETING_ADAPTERS } from './ports/live-meeting.port.js';
import type { LiveRoomRepository } from './ports/live-room.repository.js';
import { LIVE_ROOM_REPOSITORY } from './ports/live-room.repository.js';

export interface CreateLiveRoomResult {
  access: HostAccess;
  created: boolean;
}

@Injectable()
export class LiveRoomService {
  constructor(
    @Inject(LIVE_ROOM_REPOSITORY)
    private readonly repository: LiveRoomRepository,
    @Inject(LIVE_MEETING_ADAPTERS)
    private readonly adapters: Map<LiveProvider, LiveMeetingPort>,
    private readonly cipher: SecretCipher,
    private readonly metrics: LiveRoomMetrics,
  ) {}

  async createRoom(
    assignmentId: string,
    provider: LiveProvider,
    user: AuthenticatedUser,
    correlationId: string | null,
  ): Promise<CreateLiveRoomResult> {
    if (!isSupportedProvider(provider)) {
      throw DomainErrors.unsupportedProvider(provider);
    }

    const assignment = await this.repository.findAssignment(assignmentId);
    this.authorizeWrite(assignment, user);

    if (!isLiveMode(assignment.mode)) {
      throw DomainErrors.liveRoomNotApplicable(
        'La asignación no está en modalidad LIVE',
      );
    }
    if (!canCreateLiveRoom(assignment.status)) {
      throw DomainErrors.invalidStateTransition(
        `No se puede crear la sala en estado ${assignment.status}`,
      );
    }

    const existing = await this.repository.findRoomByAssignment(assignmentId);
    if (existing) {
      return this.reuseExisting(existing, provider);
    }

    return this.repository.runLocked(assignmentId, async (tx) => {
      const locked = await tx.findRoom(assignmentId);
      if (locked) {
        return this.reuseExisting(locked, provider);
      }

      const adapter = this.adapterFor(provider);
      const result = await this.callProvider(provider, adapter, assignment);

      const room = await tx.saveRoom({
        id: randomUUID(),
        assignmentId,
        provider,
        externalRoomId: result.externalRoomId,
        joinUrl: result.joinUrl,
        hostUrl: result.hostUrl ?? null,
        passcodeEnc: this.encryptOptional(result.passcode),
        hostSignatureEnc: this.encryptOptional(result.hostSignature),
        inviteUrlEnc: this.encryptOptional(result.inviteUrl),
        syncState: LiveRoomSyncState.SYNCED,
      });

      await tx.saveOutbox(
        this.buildEvent(assignmentId, provider, correlationId),
      );

      return { access: this.toHostAccess(room), created: true };
    });
  }

  async getAccess(
    assignmentId: string,
    user: AuthenticatedUser,
  ): Promise<ParticipantAccess | HostAccess> {
    const assignment = await this.repository.findAssignment(assignmentId);
    const isOwnerCustomer =
      !!assignment && user.role === 'CUSTOMER' && assignment.customerId === user.uid;
    const isOwnerProvider =
      !!assignment && user.role === 'PROVIDER' && assignment.providerId === user.uid;
    const isAdmin = user.role === 'ADMIN';

    if (!assignment || (!isOwnerCustomer && !isOwnerProvider && !isAdmin)) {
      throw DomainErrors.notFound('Asignación no encontrada');
    }

    const room = await this.repository.findRoomByAssignment(assignmentId);
    if (!room) {
      throw DomainErrors.notFound('La asignación no tiene sala en vivo');
    }

    if (isTerminalStatus(assignment.status)) {
      throw DomainErrors.forbidden('La asignación está cerrada');
    }
    if (!isWithinAccessWindow(assignment, new Date())) {
      throw DomainErrors.outsideAccessWindow();
    }

    return isOwnerCustomer && !isAdmin
      ? this.toParticipantAccess(room)
      : this.toHostAccess(room);
  }

  private reuseExisting(
    room: LiveRoomRecord,
    provider: LiveProvider,
  ): CreateLiveRoomResult {
    if (room.provider !== provider) {
      throw DomainErrors.liveRoomAlreadyExists(room.provider);
    }
    return { access: this.toHostAccess(room), created: false };
  }

  private authorizeWrite(
    assignment: AssignmentSnapshot | null,
    user: AuthenticatedUser,
  ): asserts assignment is AssignmentSnapshot {
    if (user.role === 'CUSTOMER') {
      throw DomainErrors.forbidden('El rol CUSTOMER no puede crear la sala');
    }
    if (
      !assignment ||
      (user.role === 'PROVIDER' && assignment.providerId !== user.uid)
    ) {
      throw DomainErrors.notFound('Asignación no encontrada');
    }
  }

  private adapterFor(provider: LiveProvider): LiveMeetingPort {
    const adapter = this.adapters.get(provider);
    if (!adapter) {
      throw DomainErrors.providerUnavailable(
        provider,
        'adaptador no registrado',
      );
    }
    return adapter;
  }

  /** Mide y clasifica cada intento externo (NFR-003). */
  private async callProvider(
    provider: LiveProvider,
    adapter: LiveMeetingPort,
    assignment: AssignmentSnapshot,
  ) {
    this.metrics.recordAttempt(provider);
    const startedAt = Date.now();
    try {
      const result = await adapter.createRoom({ assignment });
      this.metrics.recordSuccess(provider, Date.now() - startedAt);
      return result;
    } catch (error) {
      const latency = Date.now() - startedAt;
      if (error instanceof DomainError && error.code === 'LIVE_PROVIDER_TIMEOUT') {
        this.metrics.recordTimeout(provider, latency);
      } else {
        this.metrics.recordFailure(provider, latency);
      }
      throw error;
    }
  }

  private buildEvent(
    assignmentId: string,
    provider: LiveProvider,
    correlationId: string | null,
  ): OutboxRecord {
    return {
      eventId: randomUUID(),
      eventType: 'MentorshipLiveRoomCreated',
      aggregateType: 'Mentorship',
      aggregateId: assignmentId,
      version: 1,
      correlationId,
      occurredAt: new Date(),
      payload: { assignmentId, provider, fallbackUsed: false },
    };
  }

  private toParticipantAccess(room: LiveRoomRecord): ParticipantAccess {
    return {
      assignmentId: room.assignmentId,
      externalRoomId: room.externalRoomId,
      provider: room.provider,
      joinUrl: room.joinUrl,
      passcode: this.decryptOptional(room.passcodeEnc),
      inviteUrl: this.decryptOptional(room.inviteUrlEnc),
    };
  }

  private toHostAccess(room: LiveRoomRecord): HostAccess {
    return {
      assignmentId: room.assignmentId,
      externalRoomId: room.externalRoomId,
      provider: room.provider,
      joinUrl: room.joinUrl,
      hostUrl: room.hostUrl,
      hostSignature: this.decryptOptional(room.hostSignatureEnc),
      passcode: this.decryptOptional(room.passcodeEnc),
    };
  }

  private encryptOptional(value?: string | null): string | null {
    return value ? this.cipher.encrypt(value) : null;
  }

  private decryptOptional(value: string | null): string | null {
    return value ? this.cipher.decrypt(value) : null;
  }
}
