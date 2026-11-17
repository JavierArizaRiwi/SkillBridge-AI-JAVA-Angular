import type {
  AssignmentSnapshot,
  LiveProvider,
  ProviderRoomResult,
} from '../domain/live-room.types.js';

export interface CreateRoomInput {
  assignment: AssignmentSnapshot;
}

/**
 * Puerto de salida hacia un proveedor de reuniones. Cada adaptador encapsula
 * su API externa; el dominio nunca importa SDKs (Artículo I).
 */
export interface LiveMeetingPort {
  readonly provider: LiveProvider;
  createRoom(input: CreateRoomInput): Promise<ProviderRoomResult>;
  findExistingRoom(assignmentId: string): Promise<ProviderRoomResult | null>;
}

export const LIVE_MEETING_ADAPTERS = Symbol('LIVE_MEETING_ADAPTERS');
