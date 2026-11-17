/**
 * Tipos y reglas puras del aula en vivo (HU-MNT-07).
 * Este archivo no importa framework ni SDKs externos.
 */

export enum LiveProvider {
  ZOOM = 'ZOOM',
  MEET = 'MEET',
  DISCORD = 'DISCORD',
  CUSTOM_URL = 'CUSTOM_URL',
}

/** Proveedores que esta HU implementa (CUSTOM_URL es de HU-MNT-09). */
export const SUPPORTED_LIVE_PROVIDERS: readonly LiveProvider[] = [
  LiveProvider.ZOOM,
  LiveProvider.MEET,
  LiveProvider.DISCORD,
];

export enum MentorshipMode {
  ASYNCHRONOUS = 'ASYNCHRONOUS',
  LIVE = 'LIVE',
}

export enum MentorshipStatus {
  PENDING_PAYMENT = 'PENDING_PAYMENT',
  SCHEDULED = 'SCHEDULED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  EXPIRED = 'EXPIRED',
  NO_SHOW = 'NO_SHOW',
}

export const TERMINAL_MENTORSHIP_STATUSES: readonly MentorshipStatus[] = [
  MentorshipStatus.COMPLETED,
  MentorshipStatus.CANCELLED,
  MentorshipStatus.EXPIRED,
  MentorshipStatus.NO_SHOW,
];

export enum LiveRoomSyncState {
  PENDING = 'PENDING',
  SYNCED = 'SYNCED',
  FAILED = 'FAILED',
}

export interface AssignmentSnapshot {
  id: string;
  bookingId: string;
  customerId: string;
  providerId: string;
  offeringId: string;
  mode: MentorshipMode;
  status: MentorshipStatus;
  accessStartsAt: Date;
  accessEndsAt: Date;
}

export interface ProviderRoomResult {
  externalRoomId: string;
  joinUrl: string;
  hostUrl?: string | null;
  passcode?: string | null;
  hostSignature?: string | null;
  inviteUrl?: string | null;
}

export interface ParticipantAccess {
  assignmentId: string;
  externalRoomId: string;
  provider: LiveProvider;
  joinUrl: string;
  passcode: string | null;
  inviteUrl: string | null;
}

export interface HostAccess {
  assignmentId: string;
  externalRoomId: string;
  provider: LiveProvider;
  joinUrl: string;
  hostUrl: string | null;
  hostSignature: string | null;
  passcode: string | null;
}

export interface LiveRoomRecord {
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
  createdAt: Date;
  updatedAt: Date;
}

export interface OutboxRecord {
  eventId: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  version: number;
  correlationId: string | null;
  payload: Record<string, unknown>;
  occurredAt: Date;
}

export function isTerminalStatus(status: MentorshipStatus): boolean {
  return TERMINAL_MENTORSHIP_STATUSES.includes(status);
}

/** La sala solo puede prepararse desde SCHEDULED o IN_PROGRESS (FR-001). */
export function canCreateLiveRoom(status: MentorshipStatus): boolean {
  return (
    status === MentorshipStatus.SCHEDULED ||
    status === MentorshipStatus.IN_PROGRESS
  );
}

export function isLiveMode(mode: MentorshipMode): boolean {
  return mode === MentorshipMode.LIVE;
}

/** Ventana `[accessStartsAt, accessEndsAt)` (contrato state-machine). */
export function isWithinAccessWindow(
  assignment: Pick<AssignmentSnapshot, 'accessStartsAt' | 'accessEndsAt'>,
  now: Date,
): boolean {
  return now >= assignment.accessStartsAt && now < assignment.accessEndsAt;
}

export function isSupportedProvider(provider: LiveProvider): boolean {
  return SUPPORTED_LIVE_PROVIDERS.includes(provider);
}
