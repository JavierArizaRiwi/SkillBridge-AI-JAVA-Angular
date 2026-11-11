import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { ReservationStatus } from '../enums/index.js';
import { FilterReservationsDto } from './filter-reservations.dto.js';

/** Convierte los query params en el DTO, igual que hace el ValidationPipe. */
function toDto(query: object): FilterReservationsDto {
  return plainToInstance(FilterReservationsDto, query);
}

/** Devuelve los nombres de los campos que NO pasan la validación. */
async function invalidProps(query: object): Promise<string[]> {
  const errors = await validate(toDto(query));
  return errors.map((e) => e.property).sort();
}

describe('FilterReservationsDto', () => {
  it('sin filtros es válido y usa page 1 y limit 10', async () => {
    const dto = toDto({});
    expect(await invalidProps({})).toEqual([]);
    expect(dto.page).toBe(1);
    expect(dto.limit).toBe(10);
  });

  it('convierte page y limit de texto a número', () => {
    const dto = toDto({ page: '2', limit: '20' });
    expect(dto.page).toBe(2);
    expect(dto.limit).toBe(20);
  });

  it('acepta una fecha y una mesa válidas', async () => {
    expect(
      await invalidProps({
        date: '2026-10-10',
        tableId: '0b6f3c2e-8c1a-4d5e-9f7a-1b2c3d4e5f60',
      }),
    ).toEqual([]);
  });

  it('rechaza una fecha que no tiene formato YYYY-MM-DD', async () => {
    expect(await invalidProps({ date: 'hola' })).toEqual(['date']);
  });

  it('rechaza un tableId que no es UUID', async () => {
    expect(await invalidProps({ tableId: 'mesa-5' })).toEqual(['tableId']);
  });

  it('acepta todos los estados, incluidos CANCELLED y COMPLETED (RN-051)', async () => {
    for (const status of Object.values(ReservationStatus)) {
      expect(await invalidProps({ status })).toEqual([]);
    }
  });

  it('rechaza un estado que no existe', async () => {
    expect(await invalidProps({ status: 'BORRADA' })).toEqual(['status']);
  });

  it.each([
    ['cero', '0'],
    ['negativa', '-1'],
    ['decimal', '1.5'],
    ['texto', 'abc'],
  ])('rechaza page %s', async (_label, page) => {
    expect(await invalidProps({ page })).toEqual(['page']);
  });

  it.each([
    ['cero', '0'],
    ['mayor que 100', '500'],
  ])('rechaza limit %s', async (_label, limit) => {
    expect(await invalidProps({ limit })).toEqual(['limit']);
  });
});
