import { Test } from '@nestjs/testing';
import { CategoriesController } from './categories.controller.js';
import { CategoriesService } from './categories.service.js';
import { CategoryStatus } from './enums/index.js';
import { Category } from './entities/category.entity.js';

const CATEGORY_ID = '4f3c2a1e-9b8d-4c7a-a6e5-1d2c3b4a5f60';

function categoryFixture(overrides: Partial<Category> = {}): Category {
  return {
    id: CATEGORY_ID,
    name: 'Entradas',
    description: 'Platos ligeros para comenzar',
    status: CategoryStatus.ACTIVE,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

describe('CategoriesController', () => {
  let controller: CategoriesController;
  const service = {
    create: vi.fn(),
    findAll: vi.fn(),
    findOne: vi.fn(),
    update: vi.fn(),
    updateStatus: vi.fn(),
  };

  beforeEach(async () => {
    vi.resetAllMocks();

    const moduleRef = await Test.createTestingModule({
      controllers: [CategoriesController],
      providers: [{ provide: CategoriesService, useValue: service }],
    }).compile();

    controller = moduleRef.get(CategoriesController);
  });

  describe('create', () => {
    it('delega la creación al servicio', async () => {
      const dto = { name: 'Postres', description: 'Delicias' };
      const expected = categoryFixture(dto);
      service.create.mockResolvedValue(expected);

      const result = await controller.create(dto);

      expect(service.create).toHaveBeenCalledWith(dto);
      expect(result).toBe(expected);
    });
  });

  describe('findAll', () => {
    it('delega la consulta de todas las categorías al servicio', async () => {
      const expected = [categoryFixture()];
      service.findAll.mockResolvedValue(expected);

      const result = await controller.findAll();

      expect(service.findAll).toHaveBeenCalled();
      expect(result).toBe(expected);
    });
  });

  describe('findOne', () => {
    it('delega la consulta por ID al servicio', async () => {
      const expected = categoryFixture();
      service.findOne.mockResolvedValue(expected);

      const result = await controller.findOne(CATEGORY_ID);

      expect(service.findOne).toHaveBeenCalledWith(CATEGORY_ID);
      expect(result).toBe(expected);
    });
  });

  describe('update', () => {
    it('delega la actualización al servicio', async () => {
      const dto = { name: 'Entradas Especiales' };
      const expected = categoryFixture(dto);
      service.update.mockResolvedValue(expected);

      const result = await controller.update(CATEGORY_ID, dto);

      expect(service.update).toHaveBeenCalledWith(CATEGORY_ID, dto);
      expect(result).toBe(expected);
    });
  });

  describe('updateStatus', () => {
    it('delega el cambio de estado al servicio', async () => {
      const dto = { status: CategoryStatus.INACTIVE };
      const expected = categoryFixture(dto);
      service.updateStatus.mockResolvedValue(expected);

      const result = await controller.updateStatus(CATEGORY_ID, dto);

      expect(service.updateStatus).toHaveBeenCalledWith(CATEGORY_ID, dto);
      expect(result).toBe(expected);
    });
  });
});
