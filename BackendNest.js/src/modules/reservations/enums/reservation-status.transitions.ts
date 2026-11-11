import { BusinessRuleException } from '../../../common/exceptions/business-rule.exception.js';
import { ReservationStatus } from './reservation-status.enum.js';

export const ALLOWED_TRANSITIONS: Readonly<
  Record<ReservationStatus, readonly ReservationStatus[]>
> = {
  [ReservationStatus.PENDING]: [
    ReservationStatus.CONFIRMED,
    ReservationStatus.CANCELLED,
  ],
  [ReservationStatus.CONFIRMED]: [
    ReservationStatus.CHECKED_IN,
    ReservationStatus.CANCELLED,
    ReservationStatus.NO_SHOW,
  ],
  [ReservationStatus.CHECKED_IN]: [ReservationStatus.COMPLETED],
  [ReservationStatus.CANCELLED]: [],
  [ReservationStatus.NO_SHOW]: [],
  [ReservationStatus.COMPLETED]: [],
};

export const BLOCKING_STATUSES: readonly ReservationStatus[] = [
  ReservationStatus.PENDING,
  ReservationStatus.CONFIRMED,
  ReservationStatus.CHECKED_IN,
];

const RULE_BY_TARGET: Partial<Record<ReservationStatus, string>> = {
  [ReservationStatus.CONFIRMED]: 'RN-066', // solo se confirman reservas PENDING
  [ReservationStatus.CHECKED_IN]: 'RN-071', // check-in solo desde CONFIRMED
  [ReservationStatus.NO_SHOW]: 'RN-077', // no-show solo desde CONFIRMED
  [ReservationStatus.CANCELLED]: 'RN-061', // no se cancela con CHECKED_IN
};

export function canTransition(
  from: ReservationStatus,
  to: ReservationStatus,
): boolean {
  return ALLOWED_TRANSITIONS[from].includes(to);
}

export function assertTransition(
  from: ReservationStatus,
  to: ReservationStatus,
): void {
  if (canTransition(from, to)) {
    return; // transición válida: no pasa nada y el flujo continúa
  }

  const allowed = ALLOWED_TRANSITIONS[from];

  if (allowed.length === 0) {
    throw new BusinessRuleException(
      `La reserva está en estado ${from}, que es final y no admite cambios`,
      'RN-057',
    );
  }

  throw new BusinessRuleException(
    `No se puede cambiar una reserva de ${from} a ${to}. Desde ${from} solo se permite: ${allowed.join(', ')}`,
    RULE_BY_TARGET[to],
  );
}
