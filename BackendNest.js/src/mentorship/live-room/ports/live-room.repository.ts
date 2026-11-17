import type {
  AssignmentSnapshot,
  LiveProvider,
  LiveRoomRecord,
  LiveRoomSyncState,
  OutboxRecord,
} from '../domain/live-room.types.js';

export interface NewLiveRoom {
  id: string;
  assignmentId: string;
  provider: LiveProvider;
  externalRoomId: string;
  joinUrl: string;
  hostUrl: string | null;
  passcodeEnc: string | null;
  hostSignatureEnc: string | null;
  inviteUrlEnc: string | null;
  syncState: LiveRoomSyncState;
}

export interface LiveRoomTransaction {
  findRoom(assignmentId: string): Promise<LiveRoomRecord | null>;
  saveRoom(room: NewLiveRoom): Promise<LiveRoomRecord>;
  saveOutbox(event: OutboxRecord): Promise<void>;
}

/**
 * Repositorio de salas. `runLocked` ejecuta el trabajo dentro de una
 * transacción con bloqueo asesor por asignación, garantizando una sola
 * llamada externa y la atomicidad sala + outbox (FR-015, FR-017).
 */
export interface LiveRoomRepository {
  findAssignment(assignmentId: string): Promise<AssignmentSnapshot | null>;
  findRoomByAssignment(assignmentId: string): Promise<LiveRoomRecord | null>;
  runLocked<T>(
    assignmentId: string,
    work: (tx: LiveRoomTransaction) => Promise<T>,
  ): Promise<T>;
}

export const LIVE_ROOM_REPOSITORY = Symbol('LIVE_ROOM_REPOSITORY');
