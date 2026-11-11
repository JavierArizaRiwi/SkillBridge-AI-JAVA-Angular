import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { ZEROBUG } from '../../../common/constants/restaurant-timezone.js';
import { CreateReservationDto, UpdateReservationDto } from './index.js';

// Devuelve los campos que fallaron, con las mismas opciones que el
// ValidationPipe global (app.setup.ts).
async function invalidProps(
  payload: Record<string, unknown>,
): Promise<string[]> {
  const errors = await validate(
    plainToInstance(CreateReservationDto, payload),
    {
      whitelist: true,
      forbidNonWhitelisted: true,
    },
  );
  return errors.map((e) => e.property).sort();
}

async function invalidUpdateProps(
  payload: Record<string, unknown>,
): Promise<string[]> {
  const errors = await validate(
    plainToInstance(UpdateReservationDto, payload),
    {
      whitelist: true,
      forbidNonWhitelisted: true,
    },
  );
  return errors.map((e) => e.property).sort();
}

// Fecha y hora de un instante, vistas en la zona horaria del restaurante: la
// misma que usa @IsFutureDateTime. Así los tests dan igual en tu computador
// (Colombia) que en el CI (UTC).
function inRestaurantTz(instant: Date): { date: string; time: string } {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: ZEROBUG,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(instant);

  const get = (type: string): string =>
    parts.find((p) => p.type === type)?.value ?? '';

  return {
    date: `${get('year')}-${get('month')}-${get('day')}`,
    time: `${get('hour')}:${get('minute')}`,
  };
}

// Fecha y hora del restaurante dentro de N horas (negativo = hace N horas).
const hoursFromNow = (hours: number): { date: string; time: string } =>
  inRestaurantTz(new Date(Date.now() + hours * 60 * 60 * 1000));

const valid = {
  customerName: 'Carlos Pérez',
  phone: '3001234567',
  email: 'carlos@example.com',
  date: hoursFromNow(24).date,
  time: '19:00',
  guests: 4,
};

describe('CreateReservationDto', () => {
  it('acepta una reserva válida', async () => {
    expect(await invalidProps(valid)).toEqual([]);
  });

  it('exige todos los campos', async () => {
    expect(await invalidProps({})).toEqual([
      'customerName',
      'date',
      'email',
      'guests',
      'phone',
      'time',
    ]);
  });

  it('rechaza campos no declarados en el DTO', async () => {
    expect(await invalidProps({ ...valid, tableId: 'abc' })).toEqual([
      'tableId',
    ]);
  });

  describe('customerName', () => {
    it('recorta los espacios del inicio y del final', () => {
      const dto = plainToInstance(CreateReservationDto, {
        ...valid,
        customerName: '  Carlos Pérez  ',
      });
      expect(dto.customerName).toBe('Carlos Pérez');
    });

    it.each([
      ['vacío', ''],
      ['una sola letra', 'A'],
      ['solo espacios', '   '],
      ['más de 100 caracteres', 'a'.repeat(101)],
      ['un número', 123],
    ])('rechaza nombre %s', async (_label, customerName) => {
      expect(await invalidProps({ ...valid, customerName })).toEqual([
        'customerName',
      ]);
    });
  });

  describe('phone', () => {
    it.each(['3001234567', '+57 300 123 4567', '(601) 234-5678'])(
      'acepta el formato %s',
      async (phone) => {
        expect(await invalidProps({ ...valid, phone })).toEqual([]);
      },
    );

    it.each([
      ['vacío', ''],
      ['muy corto', '12345'],
      ['con letras', '300abc4567'],
      ['más de 15 dígitos', '1234567890123456'],
      ['un número sin comillas', 3001234567],
    ])('rechaza teléfono %s', async (_label, phone) => {
      expect(await invalidProps({ ...valid, phone })).toEqual(['phone']);
    });
  });

  describe('email', () => {
    it.each([
      ['vacío', ''],
      ['sin arroba', 'carlos.example.com'],
      ['sin dominio', 'carlos@'],
    ])('rechaza email %s', async (_label, email) => {
      expect(await invalidProps({ ...valid, email })).toEqual(['email']);
    });
  });

  describe('date', () => {
    // Con el formato roto, @IsFutureDateTime (que está en time) tampoco puede
    // armar la fecha y hora, así que fallan los dos campos.
    it.each([
      ['con formato dd/mm/aaaa', '20/12/2030'],
      ['sin ceros', '2030-9-5'],
    ])('rechaza fecha %s', async (_label, date) => {
      expect(await invalidProps({ ...valid, date })).toEqual(['date', 'time']);
    });

    // Formato correcto pero día inexistente: solo falla date.
    it('rechaza una fecha que no existe', async () => {
      expect(await invalidProps({ ...valid, date: '2030-02-30' })).toEqual([
        'date',
      ]);
    });
  });

  describe('time', () => {
    it.each(['25:00', '19:60', '7pm', '19:00:00', ''])(
      'rechaza la hora "%s"',
      async (time) => {
        expect(await invalidProps({ ...valid, time })).toEqual(['time']);
      },
    );
  });

  describe('fecha y hora futuras en la zona del restaurante (RN-042)', () => {
    it('rechaza una fecha de ayer', async () => {
      const date = hoursFromNow(-24).date;
      expect(await invalidProps({ ...valid, date })).toEqual(['time']);
    });

    it('rechaza hoy con una hora que ya pasó', async () => {
      const past = hoursFromNow(-2);
      expect(await invalidProps({ ...valid, ...past })).toEqual(['time']);
    });

    it('acepta hoy con una hora futura', async () => {
      const future = hoursFromNow(2);
      expect(await invalidProps({ ...valid, ...future })).toEqual([]);
    });
  });

  describe('guests (RN-043)', () => {
    it.each([
      ['cero', 0],
      ['negativo', -2],
      ['decimal', 2.5],
      ['texto', '4'],
    ])('rechaza guests %s', async (_label, guests) => {
      expect(await invalidProps({ ...valid, guests })).toEqual(['guests']);
    });
  });
});

describe('UpdateReservationDto', () => {
  it('solo time → válido', async () => {
    expect(await invalidUpdateProps({ time: '20:30' })).toEqual([]);
  });

  it('guests: 0 → error', async () => {
    expect(await invalidUpdateProps({ guests: 0 })).toEqual(['guests']);
  });

  it('email inválido → error', async () => {
    expect(await invalidUpdateProps({ email: 'invalido' })).toEqual(['email']);
  });

  it('status → error (no está en el DTO)', async () => {
    expect(await invalidUpdateProps({ status: 'CONFIRMED' })).toEqual([
      'status',
    ]);
  });
});
