import { validate } from 'class-validator';
import { AvailabilityQueryDto } from '../../modules/reservations/dto/availability-query.dto.js';

function build(date: string, time: string, guests: number) {
  const dto = new AvailabilityQueryDto();
  dto.date = date;
  dto.time = time;
  dto.guests = guests;
  return dto;
}

describe('AvailabilityQueryDto', () => {
  beforeEach(() => {
    vi.useFakeTimers();

    // 2026-10-10 12:00 en Bogotá = 17:00 UTC
    vi.setSystemTime(new Date('2026-10-10T17:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('acepta una fecha y hora futuras', async () => {
    const errors = await validate(build('2026-10-10', '20:00', 2));

    expect(errors).toHaveLength(0);
  });

  it('rechaza una fecha de ayer', async () => {
    const errors = await validate(build('2026-10-09', '20:00', 2));

    expect(errors.map((error) => error.property)).toContain('time');
  });

  it('rechaza hoy con una hora que ya pasó', async () => {
    const errors = await validate(build('2026-10-10', '11:00', 2));

    expect(errors.map((error) => error.property)).toContain('time');
  });

  it('rechaza guests = 0', async () => {
    const errors = await validate(build('2026-10-10', '20:00', 0));

    expect(errors.map((error) => error.property)).toContain('guests');
  });

  it('rechaza formatos inválidos', async () => {
    const errors = await validate(build('2026-02-31', '25:00', 2));

    expect(errors).not.toHaveLength(0);
  });
});
