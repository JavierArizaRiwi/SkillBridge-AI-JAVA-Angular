import { BusinessRuleException } from '../../../common/exceptions/index.js';
import { ReservationStatus } from './reservation-status.enum.js';
import {
  ALLOWED_TRANSITIONS,
  BLOCKING_STATUSES,
  assertTransition,
  canTransition,
} from './reservation-status.transitions.js';

const { PENDING, CONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW, COMPLETED } =
  ReservationStatus;

function captureError(from: ReservationStatus, to: ReservationStatus) {
  try {
    assertTransition(from, to);
  } catch (error) {
    return error;
  }
  return undefined;
}

describe('ALLOWED_TRANSITIONS', () => {
  it('define una entrada por cada estado del enum', () => {
    expect(Object.keys(ALLOWED_TRANSITIONS).sort()).toEqual(
      Object.values(ReservationStatus).sort(),
    );
  });

  it.each([CANCELLED, NO_SHOW, COMPLETED])(
    '%s es un estado final sin salida',
    (status) => {
      expect(ALLOWED_TRANSITIONS[status]).toEqual([]);
    },
  );
});

describe('BLOCKING_STATUSES', () => {
  it('bloquean la mesa solo PENDING, CONFIRMED y CHECKED_IN', () => {
    expect([...BLOCKING_STATUSES].sort()).toEqual(
      [PENDING, CONFIRMED, CHECKED_IN].sort(),
    );
  });
});

describe('canTransition', () => {
  it('devuelve true para una transición permitida', () => {
    expect(canTransition(PENDING, CONFIRMED)).toBe(true);
  });

  it('devuelve false para una transición no permitida', () => {
    expect(canTransition(PENDING, COMPLETED)).toBe(false);
  });
});

describe('assertTransition', () => {
  it.each([
    [PENDING, CONFIRMED],
    [PENDING, CANCELLED],
    [CONFIRMED, CHECKED_IN],
    [CONFIRMED, CANCELLED],
    [CONFIRMED, NO_SHOW],
    [CHECKED_IN, COMPLETED],
  ])('permite %s → %s', (from, to) => {
    expect(() => assertTransition(from, to)).not.toThrow();
  });

  it.each([
    [PENDING, CHECKED_IN, 'RN-071'],
    [PENDING, NO_SHOW, 'RN-077'],
    [CONFIRMED, CONFIRMED, 'RN-066'],
    [CHECKED_IN, CANCELLED, 'RN-061'],
    [CANCELLED, CONFIRMED, 'RN-057'],
    [NO_SHOW, CHECKED_IN, 'RN-057'],
    [COMPLETED, CANCELLED, 'RN-057'],
  ])('rechaza %s → %s con 409 y la regla %s', (from, to, rule) => {
    const error = captureError(from, to);

    expect(error).toBeInstanceOf(BusinessRuleException);
    expect((error as BusinessRuleException).getStatus()).toBe(409);
    expect((error as BusinessRuleException).getResponse()).toMatchObject({
      rule,
    });
  });

  it('rechaza una transición sin regla asociada (PENDING → COMPLETED)', () => {
    expect(() => assertTransition(PENDING, COMPLETED)).toThrow(
      BusinessRuleException,
    );
  });

  it('el mensaje indica a qué estados sí se puede pasar', () => {
    const error = captureError(PENDING, CHECKED_IN) as BusinessRuleException;

    expect(JSON.stringify(error.getResponse())).toContain(
      'CONFIRMED, CANCELLED',
    );
  });
});
