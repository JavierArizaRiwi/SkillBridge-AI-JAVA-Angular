import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { CategoryStatus } from '../enums/index.js';
import {
  CreateCategoryDto,
  UpdateCategoryDto,
  UpdateCategoryStatusDto,
} from './index.js';

async function invalidProps(
  cls: new () => object,
  payload: object,
): Promise<string[]> {
  const errors = await validate(plainToInstance(cls, payload));
  return errors.map((error) => error.property).sort();
}

describe('CreateCategoryDto', () => {
  it('acepta una categoría válida', async () => {
    expect(
      await invalidProps(CreateCategoryDto, {
        name: 'Entradas',
        description: 'Para comenzar',
      }),
    ).toEqual([]);
  });

  it.each(['', 'A'])(
    'rechaza un nombre de longitud inválida: "%s"',
    async (name) => {
      expect(await invalidProps(CreateCategoryDto, { name })).toEqual(['name']);
    },
  );

  it('rechaza un nombre ausente', async () => {
    expect(await invalidProps(CreateCategoryDto, {})).toEqual(['name']);
  });
});

describe('UpdateCategoryDto', () => {
  it('permite una actualización parcial sin status', async () => {
    expect(
      await invalidProps(UpdateCategoryDto, { description: 'Nuevas opciones' }),
    ).toEqual([]);
  });

  it('valida el nombre cuando se envía', async () => {
    expect(await invalidProps(UpdateCategoryDto, { name: 'A' })).toEqual([
      'name',
    ]);
  });

  it('rechaza status: se cambia por PATCH /categories/{id}/status', async () => {
    // Mismas opciones que el ValidationPipe global (app.setup.ts)
    const errors = await validate(
      plainToInstance(UpdateCategoryDto, { status: CategoryStatus.INACTIVE }),
      { whitelist: true, forbidNonWhitelisted: true },
    );
    expect(errors.map((e) => e.property)).toEqual(['status']);
  });
});

describe('UpdateCategoryStatusDto', () => {
  it('acepta estados definidos por el sistema', async () => {
    expect(
      await invalidProps(UpdateCategoryStatusDto, {
        status: CategoryStatus.INACTIVE,
      }),
    ).toEqual([]);
  });

  it('rechaza estados no definidos', async () => {
    expect(
      await invalidProps(UpdateCategoryStatusDto, { status: 'DELETED' }),
    ).toEqual(['status']);
  });
});
