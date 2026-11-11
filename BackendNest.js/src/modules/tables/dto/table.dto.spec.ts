import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { TableStatus, TableZone } from '../enums/index.js';
import {
  CreateTableDto,
  FilterTablesDto,
  UpdateTableDto,
  UpdateTableStatusDto,
} from './index.js';

/** Devuelve los nombres de los campos que NO pasan la validación. */
async function invalidProps(
  cls: new () => object,
  payload: object,
): Promise<string[]> {
  const errors = await validate(plainToInstance(cls, payload));
  return errors.map((e) => e.property).sort();
}

const valid = { number: 5, capacity: 4, zone: TableZone.INTERIOR };

describe('CreateTableDto', () => {
  it('acepta una mesa válida', async () => {
    expect(await invalidProps(CreateTableDto, valid)).toEqual([]);
  });

  it.each([
    ['cero', 0],
    ['negativo', -3],
    ['decimal', 4.5],
    ['como texto', '5'],
  ])('rechaza número de mesa %s', async (_label, number) => {
    expect(await invalidProps(CreateTableDto, { ...valid, number })).toEqual([
      'number',
    ]);
  });

  it.each([
    ['cero', 0],
    ['negativa', -1],
    ['decimal', 2.5],
  ])('rechaza capacidad %s (RN-017)', async (_label, capacity) => {
    expect(await invalidProps(CreateTableDto, { ...valid, capacity })).toEqual([
      'capacity',
    ]);
  });

  it('rechaza una zona fuera del enum', async () => {
    expect(
      await invalidProps(CreateTableDto, { ...valid, zone: 'PLAYA' }),
    ).toEqual(['zone']);
  });

  it('reporta todos los obligatorios ausentes', async () => {
    expect(await invalidProps(CreateTableDto, {})).toEqual([
      'capacity',
      'number',
      'zone',
    ]);
  });
});

describe('UpdateTableDto', () => {
  it('todos los campos son opcionales', async () => {
    expect(await invalidProps(UpdateTableDto, {})).toEqual([]);
  });

  it('valida los campos que sí vienen', async () => {
    expect(await invalidProps(UpdateTableDto, { capacity: 0 })).toEqual([
      'capacity',
    ]);
  });

  it('rechaza status: se cambia por PATCH /tables/{id}/status', async () => {
    // Mismas opciones que el ValidationPipe global (app.setup.ts)
    const errors = await validate(
      plainToInstance(UpdateTableDto, { status: TableStatus.OCCUPIED }),
      { whitelist: true, forbidNonWhitelisted: true },
    );
    expect(errors.map((e) => e.property)).toEqual(['status']);
  });
});

describe('UpdateTableStatusDto', () => {
  it('acepta un estado del enum', async () => {
    expect(
      await invalidProps(UpdateTableStatusDto, {
        status: TableStatus.OUT_OF_SERVICE,
      }),
    ).toEqual([]);
  });

  it('rechaza un estado fuera del enum (RN-020)', async () => {
    expect(
      await invalidProps(UpdateTableStatusDto, { status: 'RESERVED' }),
    ).toEqual(['status']);
  });

  it('el mensaje de error enumera los estados válidos', async () => {
    const errors = await validate(
      plainToInstance(UpdateTableStatusDto, { status: 'RESERVED' }),
    );
    expect(Object.values(errors[0].constraints ?? {}).join()).toBe(
      'status debe ser uno de: AVAILABLE, OCCUPIED, OUT_OF_SERVICE',
    );
  });
});

describe('FilterTablesDto', () => {
  it('sin filtros es válido', async () => {
    expect(await invalidProps(FilterTablesDto, {})).toEqual([]);
  });

  it('convierte la capacidad del query string a número', async () => {
    const dto = plainToInstance(FilterTablesDto, { capacity: '4' });
    expect(dto.capacity).toBe(4);
    expect(await validate(dto)).toEqual([]);
  });

  it('valida cada filtro cuando viene', async () => {
    expect(
      await invalidProps(FilterTablesDto, {
        status: 'X',
        zone: 'PLAYA',
        capacity: 0,
      }),
    ).toEqual(['capacity', 'status', 'zone']);
  });
});
