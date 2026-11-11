import { registerDecorator } from 'class-validator';
import type { ValidationArguments, ValidationOptions } from 'class-validator';
import { ZEROBUG } from '../constants/restaurant-timezone.js';

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;

// "Ahora" expresado como texto 'YYYY-MM-DDTHH:mm' en la zona del restaurante.
// Al ser texto con formato fijo, se puede comparar alfabéticamente.
function nowInRestaurantTz(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: ZEROBUG,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date());

  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}

export function isFutureInRestaurantTz(date: string, time: string): boolean {
  if (!DATE_RE.test(date) || !TIME_RE.test(time)) return false;
  return `${date}T${time}` > nowInRestaurantTz();
}

// Se aplica sobre `time`; lee `date` del mismo objeto.
export function IsFutureDateTime(
  dateProperty = 'date',
  validationOptions?: ValidationOptions,
) {
  return function (object: object, propertyName: string) {
    registerDecorator({
      name: 'isFutureDateTime',
      target: object.constructor,
      propertyName,
      constraints: [dateProperty],
      options: validationOptions,
      validator: {
        validate(value: unknown, args: ValidationArguments) {
          const [dateProp] = args.constraints as [string];
          const date = (args.object as Record<string, unknown>)[dateProp];

          if (typeof date !== 'string' || typeof value !== 'string')
            return false;

          return isFutureInRestaurantTz(date, value);
        },
        defaultMessage() {
          return 'La fecha y hora deben ser futuras (zona horaria del restaurante)';
        },
      },
    });
  };
}
