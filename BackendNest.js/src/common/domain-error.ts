import { HttpStatus } from '@nestjs/common';

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'UNAUTHENTICATED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'INVALID_STATE_TRANSITION'
  | 'LIVE_ROOM_NOT_APPLICABLE'
  | 'LIVE_ROOM_ALREADY_EXISTS'
  | 'UNSUPPORTED_LIVE_PROVIDER'
  | 'LIVE_PROVIDER_UNAVAILABLE'
  | 'LIVE_PROVIDER_TIMEOUT'
  | 'OUTSIDE_ACCESS_WINDOW'
  | 'INTERNAL_ERROR';

/**
 * Error de dominio con el `code` del contrato REST
 * (`docs/contracts/mentorships/rest-api.md`) y su estado HTTP.
 */
export class DomainError extends Error {
  constructor(
    public readonly code: ErrorCode,
    message: string,
    public readonly httpStatus: number,
    public readonly rule?: string,
  ) {
    super(message);
    this.name = 'DomainError';
  }
}

export const DomainErrors = {
  validation: (message: string): DomainError =>
    new DomainError('VALIDATION_ERROR', message, HttpStatus.BAD_REQUEST),

  unauthenticated: (message = 'No autenticado'): DomainError =>
    new DomainError('UNAUTHENTICATED', message, HttpStatus.UNAUTHORIZED),

  forbidden: (message = 'No autorizado', rule?: string): DomainError =>
    new DomainError('FORBIDDEN', message, HttpStatus.FORBIDDEN, rule),

  notFound: (message = 'Recurso no encontrado'): DomainError =>
    new DomainError('NOT_FOUND', message, HttpStatus.NOT_FOUND),

  invalidStateTransition: (message: string): DomainError =>
    new DomainError(
      'INVALID_STATE_TRANSITION',
      message,
      HttpStatus.CONFLICT,
      'RN-MNT-STATE',
    ),

  liveRoomNotApplicable: (message = 'La asignación no es en vivo'): DomainError =>
    new DomainError(
      'LIVE_ROOM_NOT_APPLICABLE',
      message,
      HttpStatus.CONFLICT,
      'RN-MNT-07',
    ),

  liveRoomAlreadyExists: (provider: string): DomainError =>
    new DomainError(
      'LIVE_ROOM_ALREADY_EXISTS',
      `La asignación ya tiene una sala del proveedor ${provider}`,
      HttpStatus.CONFLICT,
      'RN-MNT-07',
    ),

  unsupportedProvider: (provider: string): DomainError =>
    new DomainError(
      'UNSUPPORTED_LIVE_PROVIDER',
      `El proveedor ${provider} no está soportado en esta versión`,
      HttpStatus.BAD_REQUEST,
    ),

  providerUnavailable: (provider: string, detail?: string): DomainError =>
    new DomainError(
      'LIVE_PROVIDER_UNAVAILABLE',
      `El proveedor ${provider} no está disponible${detail ? `: ${detail}` : ''}`,
      HttpStatus.SERVICE_UNAVAILABLE,
      'RN-MNT-07',
    ),

  providerTimeout: (provider: string): DomainError =>
    new DomainError(
      'LIVE_PROVIDER_TIMEOUT',
      `El proveedor ${provider} no respondió en el tiempo esperado`,
      HttpStatus.GATEWAY_TIMEOUT,
      'RN-MNT-07',
    ),

  outsideAccessWindow: (
    message = 'Fuera de la ventana de acceso',
  ): DomainError =>
    new DomainError(
      'OUTSIDE_ACCESS_WINDOW',
      message,
      HttpStatus.FORBIDDEN,
      'RN-MNT-04',
    ),
};
