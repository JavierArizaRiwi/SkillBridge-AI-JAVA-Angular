import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Category } from './entities/category.entity.js';
import { CategoryStatus } from './enums/index.js';
import { CategoriesService } from './categories.service.js';

const CATEGORY_ID = '4f3c2a1e-9b8d-4c7a-a6e5-1d2c3b4a5f60';
const OTHER_CATEGORY_ID = '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d';

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

describe('CategoriesService', () => {
  let service: CategoriesService;
  const categories = {
    create: vi.fn(),
    save: vi.fn(),
    find: vi.fn(),
    findOneBy: vi.fn(),
    merge: vi.fn(),
  };

  beforeEach(async () => {
    vi.resetAllMocks();
    categories.create.mockImplementation((dto) => ({ ...dto }));
    categories.save.mockImplementation(async (entity) => entity);
    categories.merge.mockImplementation((entity, dto) =>
      Object.assign(entity, dto),
    );

    const moduleRef = await Test.createTestingModule({
      providers: [
        CategoriesService,
        { provide: getRepositoryToken(Category), useValue: categories },
      ],
    }).compile();

    service = moduleRef.get(CategoriesService);
  });

  describe('create', () => {
    const dto = {
      name: 'Postres',
      description: 'Dulces y delicias',
    };

    it('guarda la categoría con estado ACTIVE cuando el nombre está disponible (RN-021, RN-022)', async () => {
      categories.findOneBy.mockResolvedValue(null);

      const result = await service.create(dto);

      expect(categories.findOneBy).toHaveBeenCalledWith({ name: dto.name });
      expect(categories.create).toHaveBeenCalledWith({
        ...dto,
        status: 'ACTIVE',
      });
      expect(categories.save).toHaveBeenCalledWith(
        expect.objectContaining({
          ...dto,
          status: CategoryStatus.ACTIVE,
        }),
      );
      expect(result).toMatchObject({
        ...dto,
        status: CategoryStatus.ACTIVE,
      });
    });

    it('rechaza un nombre duplicado con 409 y RN-021', async () => {
      categories.findOneBy.mockResolvedValue(
        categoryFixture({ name: dto.name }),
      );

      await expect(service.create(dto)).rejects.toMatchObject({
        constructor: BusinessRuleException,
        response: { rule: 'RN-021' },
      });
      expect(categories.save).not.toHaveBeenCalled();
    });
  });

  describe('findAll', () => {
    it('devuelve todas las categorías ordenadas alfabéticamente por nombre', async () => {
      const list = [
        categoryFixture({ name: 'Bebidas' }),
        categoryFixture({ name: 'Entradas' }),
      ];
      categories.find.mockResolvedValue(list);

      const result = await service.findAll();

      expect(categories.find).toHaveBeenCalledWith({
        order: { name: 'ASC' },
      });
      expect(result).toBe(list);
    });

    it('incluye categorías inactivas (RN-023: desactivar no elimina)', async () => {
      const list = [
        categoryFixture({ name: 'Bebidas', status: CategoryStatus.ACTIVE }),
        categoryFixture({ name: 'Postres', status: CategoryStatus.INACTIVE }),
      ];
      categories.find.mockResolvedValue(list);

      const result = await service.findAll();

      expect(result).toHaveLength(2);
      expect(result.some((c) => c.status === CategoryStatus.INACTIVE)).toBe(
        true,
      );
    });
  });

  describe('findOne', () => {
    it('devuelve la categoría solicitada por ID', async () => {
      const category = categoryFixture();
      categories.findOneBy.mockResolvedValue(category);

      const result = await service.findOne(CATEGORY_ID);

      expect(categories.findOneBy).toHaveBeenCalledWith({ id: CATEGORY_ID });
      expect(result).toBe(category);
    });

    it('lanza EntityNotFoundException (404) si no existe', async () => {
      categories.findOneBy.mockResolvedValue(null);

      await expect(service.findOne(CATEGORY_ID)).rejects.toBeInstanceOf(
        EntityNotFoundException,
      );
    });
  });

  describe('update', () => {
    it('actualiza la descripción sin revalidar nombre cuando el nombre no cambia', async () => {
      const existing = categoryFixture();
      categories.findOneBy.mockResolvedValue(existing);

      const result = await service.update(CATEGORY_ID, {
        description: 'Nueva descripción',
      });

      expect(categories.findOneBy).toHaveBeenCalledTimes(1);
      expect(categories.findOneBy).toHaveBeenCalledWith({ id: CATEGORY_ID });
      expect(categories.save).toHaveBeenCalledWith(
        expect.objectContaining({ description: 'Nueva descripción' }),
      );
      expect(result.description).toBe('Nueva descripción');
    });

    it('permite conservar el mismo nombre sin conflicto', async () => {
      const existing = categoryFixture({ name: 'Entradas' });
      categories.findOneBy.mockResolvedValue(existing);

      await expect(
        service.update(CATEGORY_ID, { name: 'Entradas' }),
      ).resolves.toBeDefined();
    });

    it('actualiza el nombre cuando el nuevo nombre está disponible', async () => {
      const existing = categoryFixture({ name: 'Entradas' });
      categories.findOneBy
        .mockResolvedValueOnce(existing)
        .mockResolvedValueOnce(null);

      const result = await service.update(CATEGORY_ID, { name: 'Aperitivos' });

      expect(categories.findOneBy).toHaveBeenNthCalledWith(2, {
        name: 'Aperitivos',
      });
      expect(categories.save).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Aperitivos' }),
      );
      expect(result.name).toBe('Aperitivos');
    });

    it('rechaza si el nombre ya pertenece a otra categoría (RN-021)', async () => {
      const existing = categoryFixture({ id: CATEGORY_ID, name: 'Entradas' });
      const otherCategory = categoryFixture({
        id: OTHER_CATEGORY_ID,
        name: 'Bebidas',
      });

      categories.findOneBy
        .mockResolvedValueOnce(existing)
        .mockResolvedValueOnce(otherCategory);

      await expect(
        service.update(CATEGORY_ID, { name: 'Bebidas' }),
      ).rejects.toMatchObject({
        constructor: BusinessRuleException,
        response: { rule: 'RN-021' },
      });
      expect(categories.save).not.toHaveBeenCalled();
    });

    it('lanza EntityNotFoundException (404) si la categoría no existe', async () => {
      categories.findOneBy.mockResolvedValue(null);

      await expect(
        service.update(CATEGORY_ID, { name: 'Nueva' }),
      ).rejects.toBeInstanceOf(EntityNotFoundException);
    });
  });

  describe('updateStatus', () => {
    it('cambia el estado a INACTIVE (RN-023)', async () => {
      const existing = categoryFixture({ status: CategoryStatus.ACTIVE });
      categories.findOneBy.mockResolvedValue(existing);

      const result = await service.updateStatus(CATEGORY_ID, {
        status: CategoryStatus.INACTIVE,
      });

      expect(result.status).toBe(CategoryStatus.INACTIVE);
      expect(categories.save).toHaveBeenCalledWith(
        expect.objectContaining({ status: CategoryStatus.INACTIVE }),
      );
    });

    it('cambia el estado a ACTIVE', async () => {
      const existing = categoryFixture({ status: CategoryStatus.INACTIVE });
      categories.findOneBy.mockResolvedValue(existing);

      const result = await service.updateStatus(CATEGORY_ID, {
        status: CategoryStatus.ACTIVE,
      });

      expect(result.status).toBe(CategoryStatus.ACTIVE);
      expect(categories.save).toHaveBeenCalledWith(
        expect.objectContaining({ status: CategoryStatus.ACTIVE }),
      );
    });

    it('lanza EntityNotFoundException (404) si la categoría no existe', async () => {
      categories.findOneBy.mockResolvedValue(null);

      await expect(
        service.updateStatus(CATEGORY_ID, {
          status: CategoryStatus.INACTIVE,
        }),
      ).rejects.toBeInstanceOf(EntityNotFoundException);
    });
  });
});
