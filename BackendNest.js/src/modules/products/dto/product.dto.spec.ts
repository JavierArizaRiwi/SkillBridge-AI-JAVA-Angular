import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { ProductAvailability, ProductStatus } from '../enums/index.js';
import {
  CreateProductDto,
  FilterProductsDto,
  UpdateProductAvailabilityDto,
  UpdateProductDto,
  UpdateProductStatusDto,
} from './index.js';

/** Devuelve los nombres de los campos que NO pasan la validación. */
async function invalidProps(
  cls: new () => object,
  payload: object,
): Promise<string[]> {
  const errors = await validate(plainToInstance(cls, payload));
  return errors.map((e) => e.property).sort();
}

const CATEGORY_ID = '4f3c2a1e-9b8d-4c7a-a6e5-1d2c3b4a5f60';
const valid = {
  name: 'Hamburguesa Clásica',
  price: 25000,
  categoryId: CATEGORY_ID,
};

describe('CreateProductDto', () => {
  it('acepta un producto válido sin descripción', async () => {
    expect(await invalidProps(CreateProductDto, valid)).toEqual([]);
  });

  it('acepta descripción cuando viene', async () => {
    expect(
      await invalidProps(CreateProductDto, {
        ...valid,
        description: 'Con queso',
      }),
    ).toEqual([]);
  });

  it.each([
    ['cero', 0],
    ['negativo', -1],
    ['con 3 decimales', 10.123],
    ['como texto', '25000'],
  ])('rechaza precio %s (RN-026)', async (_label, price) => {
    expect(await invalidProps(CreateProductDto, { ...valid, price })).toEqual([
      'price',
    ]);
  });

  it('rechaza categoryId que no es uuid (RN-025)', async () => {
    expect(
      await invalidProps(CreateProductDto, { ...valid, categoryId: '12' }),
    ).toEqual(['categoryId']);
  });

  it('rechaza nombre de 1 carácter', async () => {
    expect(
      await invalidProps(CreateProductDto, { ...valid, name: 'H' }),
    ).toEqual(['name']);
  });

  it('reporta todos los obligatorios ausentes', async () => {
    expect(await invalidProps(CreateProductDto, {})).toEqual([
      'categoryId',
      'name',
      'price',
    ]);
  });
});

describe('UpdateProductDto', () => {
  it('todos los campos son opcionales', async () => {
    expect(await invalidProps(UpdateProductDto, {})).toEqual([]);
  });

  it('valida los campos que sí vienen', async () => {
    expect(await invalidProps(UpdateProductDto, { price: -5 })).toEqual([
      'price',
    ]);
  });
});

describe('UpdateProductStatusDto', () => {
  it('acepta un estado del enum', async () => {
    expect(
      await invalidProps(UpdateProductStatusDto, {
        status: ProductStatus.INACTIVE,
      }),
    ).toEqual([]);
  });

  it('rechaza un estado fuera del enum', async () => {
    expect(
      await invalidProps(UpdateProductStatusDto, { status: 'DELETED' }),
    ).toEqual(['status']);
  });
});

describe('UpdateProductAvailabilityDto', () => {
  it('acepta un valor del enum', async () => {
    expect(
      await invalidProps(UpdateProductAvailabilityDto, {
        availability: ProductAvailability.UNAVAILABLE,
      }),
    ).toEqual([]);
  });

  it('rechaza un valor fuera del enum', async () => {
    expect(
      await invalidProps(UpdateProductAvailabilityDto, {
        availability: 'soldout',
      }),
    ).toEqual(['availability']);
  });
});

describe('FilterProductsDto', () => {
  it('sin filtros es válido', async () => {
    expect(await invalidProps(FilterProductsDto, {})).toEqual([]);
  });

  it('valida cada filtro cuando viene', async () => {
    expect(
      await invalidProps(FilterProductsDto, {
        categoryId: 'abc',
        status: 'X',
        availability: 'Y',
      }),
    ).toEqual(['availability', 'categoryId', 'status']);
  });
});
