import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';

import { EntityNotFoundException } from '../../common/exceptions/index.js';

import { Category } from '../categories/entities/category.entity.js';
import { CategoryStatus } from '../categories/enums/index.js';

import { ProductAvailability, ProductStatus } from '../products/enums/index.js';
import { Product } from '../products/entities/product.entity.js';

import { MenuService } from './menu.service.js';

const CATEGORY_ID = '4f3c2a1e-9b8d-4c7a-a6e5-1d2c3b4a5f60';
const INACTIVE_CATEGORY_ID = '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d';

const PRODUCT_ID = '0d1e2f3a-4b5c-4d6e-8f70-8192a3b4c5d6';

function categoryFixture(overrides: Partial<Category> = {}): Category {
  return {
    id: CATEGORY_ID,
    name: 'Hamburguesas',
    description: 'Hamburguesas de la casa',
    status: CategoryStatus.ACTIVE,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function productFixture(overrides: Partial<Product> = {}): Product {
  return {
    id: PRODUCT_ID,
    name: 'Hamburguesa Clásica',
    description: 'Hamburguesa con carne y queso',
    price: 25000,
    categoryId: CATEGORY_ID,
    category: categoryFixture(),
    status: ProductStatus.ACTIVE,
    availability: ProductAvailability.AVAILABLE,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

describe('MenuService', () => {
  let service: MenuService;

  const categories = {
    find: vi.fn(),
    findOne: vi.fn(),
  };

  const products = {
    find: vi.fn(),
    findOne: vi.fn(),
  };

  beforeEach(async () => {
    vi.resetAllMocks();

    const moduleRef = await Test.createTestingModule({
      providers: [
        MenuService,
        {
          provide: getRepositoryToken(Category),
          useValue: categories,
        },
        {
          provide: getRepositoryToken(Product),
          useValue: products,
        },
      ],
    }).compile();

    service = moduleRef.get(MenuService);
  });

  describe('getMenu', () => {
    it('devuelve solamente categorías ACTIVE con productos ACTIVE', async () => {
      const activeCategory = categoryFixture();
      const activeProduct = productFixture();

      categories.find.mockResolvedValue([activeCategory]);
      products.find.mockResolvedValue([activeProduct]);

      const result = await service.getMenu();

      expect(categories.find).toHaveBeenCalledWith({
        where: {
          status: CategoryStatus.ACTIVE,
        },
        order: {
          name: 'ASC',
        },
      });

      expect(products.find).toHaveBeenCalledWith({
        where: {
          status: ProductStatus.ACTIVE,
        },
        order: {
          name: 'ASC',
        },
      });

      expect(result.categories).toHaveLength(1);
      expect(result.categories[0].id).toBe(CATEGORY_ID);
      expect(result.categories[0].products).toHaveLength(1);
      expect(result.categories[0].products[0].id).toBe(PRODUCT_ID);
    });

    it('no muestra productos de una categoría inactiva', async () => {
      const activeCategory = categoryFixture();

      const productFromInactiveCategory = productFixture({
        categoryId: INACTIVE_CATEGORY_ID,
      });

      categories.find.mockResolvedValue([activeCategory]);

      products.find.mockResolvedValue([productFromInactiveCategory]);

      const result = await service.getMenu();

      expect(result.categories[0].products).toHaveLength(0);
    });

    it('mantiene los productos UNAVAILABLE visibles', async () => {
      const activeCategory = categoryFixture();

      const unavailableProduct = productFixture({
        availability: ProductAvailability.UNAVAILABLE,
      });

      categories.find.mockResolvedValue([activeCategory]);

      products.find.mockResolvedValue([unavailableProduct]);

      const result = await service.getMenu();

      expect(result.categories[0].products).toHaveLength(1);

      expect(result.categories[0].products[0].availability).toBe(
        ProductAvailability.UNAVAILABLE,
      );
    });
  });

  describe('getActiveCategories', () => {
    it('devuelve solamente las categorías ACTIVE', async () => {
      const activeCategory = categoryFixture();

      categories.find.mockResolvedValue([activeCategory]);

      const result = await service.getActiveCategories();

      expect(categories.find).toHaveBeenCalledWith({
        where: {
          status: CategoryStatus.ACTIVE,
        },
        order: {
          name: 'ASC',
        },
      });

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe(CATEGORY_ID);
      // El listado de categorías no incluye productos: para eso está GET /menu
      expect(result[0]).not.toHaveProperty('products');
    });
  });

  describe('getProductsByCategory', () => {
    it('devuelve los productos ACTIVE de una categoría ACTIVE', async () => {
      const category = categoryFixture();
      const product = productFixture();

      categories.findOne.mockResolvedValue(category);
      products.find.mockResolvedValue([product]);

      const result = await service.getProductsByCategory(CATEGORY_ID);

      expect(categories.findOne).toHaveBeenCalledWith({
        where: {
          id: CATEGORY_ID,
          status: CategoryStatus.ACTIVE,
        },
      });

      expect(products.find).toHaveBeenCalledWith({
        where: {
          categoryId: CATEGORY_ID,
          status: ProductStatus.ACTIVE,
        },
        order: {
          name: 'ASC',
        },
      });

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe(PRODUCT_ID);
    });

    it('lanza EntityNotFoundException si la categoría no existe o está inactiva', async () => {
      categories.findOne.mockResolvedValue(null);

      await expect(
        service.getProductsByCategory(CATEGORY_ID),
      ).rejects.toBeInstanceOf(EntityNotFoundException);

      expect(products.find).not.toHaveBeenCalled();
    });
  });

  describe('getProductDetail', () => {
    it('devuelve un producto ACTIVE cuya categoría está ACTIVE', async () => {
      const product = productFixture({
        category: categoryFixture(),
      });

      products.findOne.mockResolvedValue(product);

      const result = await service.getProductDetail(PRODUCT_ID);

      expect(products.findOne).toHaveBeenCalledWith({
        where: {
          id: PRODUCT_ID,
          status: ProductStatus.ACTIVE,
        },
        relations: {
          category: true,
        },
      });

      expect(result.id).toBe(PRODUCT_ID);
      expect(result.name).toBe('Hamburguesa Clásica');
    });

    it('lanza EntityNotFoundException si el producto no existe o está INACTIVE', async () => {
      products.findOne.mockResolvedValue(null);

      await expect(service.getProductDetail(PRODUCT_ID)).rejects.toBeInstanceOf(
        EntityNotFoundException,
      );
    });

    it('lanza EntityNotFoundException si la categoría del producto está INACTIVE', async () => {
      const product = productFixture({
        category: categoryFixture({
          status: CategoryStatus.INACTIVE,
        }),
      });

      products.findOne.mockResolvedValue(product);

      await expect(service.getProductDetail(PRODUCT_ID)).rejects.toBeInstanceOf(
        EntityNotFoundException,
      );
    });

    it('mantiene la disponibilidad UNAVAILABLE en el detalle', async () => {
      const product = productFixture({
        availability: ProductAvailability.UNAVAILABLE,
      });

      products.findOne.mockResolvedValue(product);

      const result = await service.getProductDetail(PRODUCT_ID);

      expect(result.availability).toBe(ProductAvailability.UNAVAILABLE);
    });
  });
});
