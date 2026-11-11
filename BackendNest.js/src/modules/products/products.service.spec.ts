import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Category } from '../categories/entities/category.entity.js';
import { ProductAvailability, ProductStatus } from './enums/index.js';
import { Product } from './entities/product.entity.js';
import { ProductsService } from './products.service.js';

const CATEGORY_ID = '4f3c2a1e-9b8d-4c7a-a6e5-1d2c3b4a5f60';
const OTHER_CATEGORY_ID = '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d';
const PRODUCT_ID = '0d1e2f3a-4b5c-4d6e-8f70-8192a3b4c5d6';

function productFixture(overrides: Partial<Product> = {}): Product {
  return {
    id: PRODUCT_ID,
    name: 'Hamburguesa Clásica',
    description: null,
    price: 25000,
    categoryId: CATEGORY_ID,
    category: { id: CATEGORY_ID, name: 'Hamburguesas' } as Category,
    status: ProductStatus.ACTIVE,
    availability: ProductAvailability.AVAILABLE,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

describe('ProductsService', () => {
  let service: ProductsService;
  const products = {
    create: vi.fn(),
    save: vi.fn(),
    find: vi.fn(),
    findOne: vi.fn(),
    findOneBy: vi.fn(),
    merge: vi.fn(),
  };
  const categories = { existsBy: vi.fn() };

  beforeEach(async () => {
    vi.resetAllMocks();
    products.create.mockImplementation((dto) => ({ ...dto }));
    products.save.mockImplementation(async (entity) => entity);
    products.merge.mockImplementation((entity, dto) =>
      Object.assign(entity, dto),
    );

    const moduleRef = await Test.createTestingModule({
      providers: [
        ProductsService,
        { provide: getRepositoryToken(Product), useValue: products },
        { provide: getRepositoryToken(Category), useValue: categories },
      ],
    }).compile();

    service = moduleRef.get(ProductsService);
  });

  describe('create', () => {
    const dto = {
      name: 'Pasta Alfredo',
      price: 32000,
      categoryId: CATEGORY_ID,
    };

    it('guarda el producto cuando la categoría existe y el nombre está libre', async () => {
      categories.existsBy.mockResolvedValue(true);
      products.findOneBy.mockResolvedValue(null);

      const result = await service.create(dto);

      expect(products.save).toHaveBeenCalledWith(expect.objectContaining(dto));
      expect(result).toMatchObject(dto);
    });

    it('rechaza una categoría inexistente con 409 y RN-025', async () => {
      categories.existsBy.mockResolvedValue(false);

      await expect(service.create(dto)).rejects.toMatchObject({
        constructor: BusinessRuleException,
        response: { rule: 'RN-025' },
      });
      expect(products.save).not.toHaveBeenCalled();
    });

    it('rechaza un nombre repetido dentro de la misma categoría', async () => {
      categories.existsBy.mockResolvedValue(true);
      products.findOneBy.mockResolvedValue(productFixture({ name: dto.name }));

      await expect(service.create(dto)).rejects.toBeInstanceOf(
        BusinessRuleException,
      );
      expect(products.findOneBy).toHaveBeenCalledWith({
        categoryId: CATEGORY_ID,
        name: dto.name,
      });
    });
  });

  describe('findAll', () => {
    it('sin filtros consulta todo con la categoría cargada', async () => {
      products.find.mockResolvedValue([]);

      await service.findAll({});

      expect(products.find).toHaveBeenCalledWith({
        where: {},
        relations: { category: true },
        order: { name: 'ASC' },
      });
    });

    it('traduce cada filtro recibido a la cláusula where', async () => {
      products.find.mockResolvedValue([]);

      await service.findAll({
        categoryId: CATEGORY_ID,
        status: ProductStatus.ACTIVE,
        availability: ProductAvailability.UNAVAILABLE,
      });

      expect(products.find).toHaveBeenCalledWith(
        expect.objectContaining({
          where: {
            categoryId: CATEGORY_ID,
            status: ProductStatus.ACTIVE,
            availability: ProductAvailability.UNAVAILABLE,
          },
        }),
      );
    });
  });

  describe('findOne', () => {
    it('devuelve el producto con su categoría', async () => {
      const product = productFixture();
      products.findOne.mockResolvedValue(product);

      expect(await service.findOne(PRODUCT_ID)).toBe(product);
      expect(products.findOne).toHaveBeenCalledWith({
        where: { id: PRODUCT_ID },
        relations: { category: true },
      });
    });

    it('lanza 404 si no existe', async () => {
      products.findOne.mockResolvedValue(null);

      await expect(service.findOne(PRODUCT_ID)).rejects.toBeInstanceOf(
        EntityNotFoundException,
      );
    });
  });

  describe('update', () => {
    it('actualiza precio y descripción sin revalidar categoría ni nombre', async () => {
      products.findOne.mockResolvedValue(productFixture());

      await service.update(PRODUCT_ID, { price: 27000, description: 'Nueva' });

      expect(categories.existsBy).not.toHaveBeenCalled();
      expect(products.findOneBy).not.toHaveBeenCalled();
      expect(products.save).toHaveBeenCalledWith(
        expect.objectContaining({ price: 27000, description: 'Nueva' }),
      );
    });

    it('al cambiar de categoría valida que exista (RN-025) y que el nombre esté libre allí', async () => {
      products.findOne.mockResolvedValue(productFixture());
      categories.existsBy.mockResolvedValue(true);
      products.findOneBy.mockResolvedValue(null);

      await service.update(PRODUCT_ID, { categoryId: OTHER_CATEGORY_ID });

      expect(categories.existsBy).toHaveBeenCalledWith({
        id: OTHER_CATEGORY_ID,
      });
      expect(products.findOneBy).toHaveBeenCalledWith({
        categoryId: OTHER_CATEGORY_ID,
        name: 'Hamburguesa Clásica',
      });
    });

    it('rechaza cambiar a una categoría inexistente', async () => {
      products.findOne.mockResolvedValue(productFixture());
      categories.existsBy.mockResolvedValue(false);

      await expect(
        service.update(PRODUCT_ID, { categoryId: OTHER_CATEGORY_ID }),
      ).rejects.toMatchObject({ response: { rule: 'RN-025' } });
    });

    it('permite conservar el propio nombre al renombrar (no choca consigo mismo)', async () => {
      products.findOne.mockResolvedValue(productFixture());
      products.findOneBy.mockResolvedValue(productFixture());

      await expect(
        service.update(PRODUCT_ID, { name: 'Hamburguesa Clásica ' }),
      ).resolves.toBeDefined();
    });

    it('rechaza un nombre que ya usa otro producto de la categoría', async () => {
      products.findOne.mockResolvedValue(productFixture());
      products.findOneBy.mockResolvedValue(
        productFixture({ id: 'otro-id', name: 'Pasta Alfredo' }),
      );

      await expect(
        service.update(PRODUCT_ID, { name: 'Pasta Alfredo' }),
      ).rejects.toBeInstanceOf(BusinessRuleException);
    });
  });

  describe('updateStatus / updateAvailability', () => {
    it('cambia el estado (RN-029)', async () => {
      products.findOne.mockResolvedValue(productFixture());

      const result = await service.updateStatus(PRODUCT_ID, {
        status: ProductStatus.INACTIVE,
      });

      expect(result.status).toBe(ProductStatus.INACTIVE);
      expect(products.save).toHaveBeenCalledWith(
        expect.objectContaining({ status: ProductStatus.INACTIVE }),
      );
    });

    it('cambia la disponibilidad sin tocar el estado (RN-030)', async () => {
      products.findOne.mockResolvedValue(productFixture());

      const result = await service.updateAvailability(PRODUCT_ID, {
        availability: ProductAvailability.UNAVAILABLE,
      });

      expect(result.availability).toBe(ProductAvailability.UNAVAILABLE);
      expect(result.status).toBe(ProductStatus.ACTIVE);
    });

    it('404 si el producto no existe', async () => {
      products.findOne.mockResolvedValue(null);

      await expect(
        service.updateStatus(PRODUCT_ID, { status: ProductStatus.INACTIVE }),
      ).rejects.toBeInstanceOf(EntityNotFoundException);
    });
  });
});
