// Este archivo prueba TablesService SIN tocar una base de datos real.
// En vez de eso, "engañamos" al service dándole un repositorio falso
// (mock) que responde lo que nosotros le digamos.

// Vitest es la herramienta que usamos para escribir y correr pruebas.
// describe, it, expect, beforeEach y vi son sus "piezas" principales:
// - describe: agrupa varias pruebas relacionadas bajo un mismo nombre
// - it: define UNA prueba individual (también se puede escribir "test")
// - expect: la función que compara "lo que pasó" contra "lo que esperábamos"
// - beforeEach: código que se ejecuta antes de cada prueba, para dejar todo limpio
// - vi: la herramienta para crear funciones falsas (espías) que podemos controlar
import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test, TestingModule } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Repository } from 'typeorm';
import { TablesService } from './tables.service.js';
import { Table } from './entities/table.entity.js';
import { TableStatus, TableZone } from './enums/index.js';

// Esta función crea un repositorio de mentira: en vez de find/save reales
// que hablan con Postgres, son funciones vi.fn() que solo "recuerdan"
// si las llamaron y devuelven lo que nosotros configuremos en cada test.
// Piensa en vi.fn() como un actor de utilería: no hace nada de verdad,
// pero nosotros le decimos exactamente qué "actuar" en cada escena (test).
const mockRepository = () => ({
  create: vi.fn(),
  save: vi.fn(),
  findOne: vi.fn(),
  createQueryBuilder: vi.fn(),
});

// Tipo auxiliar: dice "esto se parece a un Repository<Table>, pero
// cada método es en realidad un vi.fn() que podemos controlar"
type MockRepo = Partial<Record<keyof Repository<Table>, Mock>>;

describe('TablesService', () => {
  let service: TablesService;
  let repository: MockRepo;

  // Una mesa de ejemplo que reutilizamos en varios tests,
  // para no repetir el mismo objeto una y otra vez
  const baseTable: Table = {
    id: 'uuid-1',
    number: 5,
    capacity: 4,
    zone: TableZone.INTERIOR,
    status: TableStatus.AVAILABLE,
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  // beforeEach se ejecuta ANTES de cada test individual, dejando
  // todo "limpio" y recién armado para que un test no contamine al otro.
  // Es como resetear un juego de mesa antes de cada partida.
  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TablesService,
        {
          // Le decimos a NestJS: "cuando alguien pida el repositorio
          // de Table, dale este mock en vez del real"
          provide: getRepositoryToken(Table),
          useFactory: mockRepository,
        },
      ],
    }).compile();

    service = module.get(TablesService);
    repository = module.get(getRepositoryToken(Table));
  });

  describe('create', () => {
    it('lanza BusinessRuleException si el número ya existe', async () => {
      // Simulamos que YA existe una mesa con ese número
      repository.findOne!.mockResolvedValue(baseTable);

      // expect(...).rejects.toThrow(...) verifica que la función,
      // al ejecutarse, termine lanzando ese error específico
      await expect(
        service.create({ number: 5, capacity: 4, zone: TableZone.INTERIOR }),
      ).rejects.toThrow(BusinessRuleException);
    });

    it('crea la mesa con status AVAILABLE si el número es único', async () => {
      repository.findOne!.mockResolvedValue(null); // no hay duplicado
      repository.create!.mockReturnValue(baseTable);
      repository.save!.mockResolvedValue(baseTable);

      const result = await service.create({
        number: 5,
        capacity: 4,
        zone: TableZone.INTERIOR,
      });

      // Verificamos que create() se haya llamado con status AVAILABLE
      // incluido, sin importar qué más se haya pasado (objectContaining)
      expect(repository.create).toHaveBeenCalledWith(
        expect.objectContaining({ status: TableStatus.AVAILABLE }),
      );
      expect(result).toEqual(baseTable);
    });
  });

  describe('findOne', () => {
    it('lanza EntityNotFoundException si la mesa no existe', async () => {
      repository.findOne!.mockResolvedValue(null); // no se encontró nada

      await expect(service.findOne('id-inexistente')).rejects.toThrow(
        EntityNotFoundException,
      );
    });

    it('retorna la mesa si existe', async () => {
      repository.findOne!.mockResolvedValue(baseTable);

      const result = await service.findOne('uuid-1');

      expect(result).toEqual(baseTable);
    });
  });

  describe('update', () => {
    it('revalida unicidad si el number cambia y ya existe otra mesa con ese número', async () => {
      // El service llama findOne DOS veces en este flujo:
      // 1) para traer la mesa que se va a editar
      // 2) para revisar si el nuevo número ya lo tiene otra mesa
      // mockResolvedValueOnce nos deja responder distinto en cada llamada,
      // en el orden en que ocurren
      repository
        .findOne!.mockResolvedValueOnce(baseTable) // primera llamada: la mesa a editar
        .mockResolvedValueOnce({ ...baseTable, id: 'otra-mesa' }); // segunda: choque

      await expect(service.update('uuid-1', { number: 99 })).rejects.toThrow(
        BusinessRuleException,
      );
    });

    it('no revalida unicidad si number no cambia', async () => {
      repository.findOne!.mockResolvedValueOnce(baseTable);
      repository.save!.mockResolvedValue({ ...baseTable, capacity: 6 });

      const result = await service.update('uuid-1', { capacity: 6 });

      // Si findOne solo se llamó UNA vez, confirma que NO se disparó
      // la revalidación de número (porque no vino 'number' en el dto)
      expect(repository.findOne).toHaveBeenCalledTimes(1);
      expect(result.capacity).toBe(6);
    });
  });

  describe('updateStatus', () => {
    it('lanza EntityNotFoundException si la mesa no existe', async () => {
      repository.findOne!.mockResolvedValue(null);

      await expect(
        service.updateStatus('id-inexistente', {
          status: TableStatus.OCCUPIED,
        }),
      ).rejects.toThrow(EntityNotFoundException);
    });

    it('actualiza el status si la mesa existe', async () => {
      repository.findOne!.mockResolvedValue(baseTable);
      repository.save!.mockResolvedValue({
        ...baseTable,
        status: TableStatus.OCCUPIED,
      });

      const result = await service.updateStatus('uuid-1', {
        status: TableStatus.OCCUPIED,
      });

      expect(result.status).toBe(TableStatus.OCCUPIED);
    });
  });

  describe('findAll', () => {
    it('aplica filtros combinados de status, zone y capacity', async () => {
      // Como findAll usa createQueryBuilder (no find directo), tenemos
      // que simular ese "constructor de consultas" con sus propios
      // métodos encadenados: orderBy(), andWhere(), getMany()
      const andWhere = vi.fn().mockReturnThis(); // mockReturnThis permite encadenar .andWhere().andWhere()...
      const queryBuilderMock = {
        orderBy: vi.fn().mockReturnThis(),
        andWhere,
        getMany: vi.fn().mockResolvedValue([baseTable]),
      };
      repository.createQueryBuilder!.mockReturnValue(queryBuilderMock);

      const result = await service.findAll({
        status: TableStatus.AVAILABLE,
        zone: TableZone.INTERIOR,
        capacity: 2,
      });

      // Si mandamos los 3 filtros, andWhere() debió llamarse 3 veces
      // (una por cada condición: status, zone, capacity)
      expect(andWhere).toHaveBeenCalledTimes(3);
      expect(result).toEqual([baseTable]);
    });

    it('no aplica filtros si vienen vacíos', async () => {
      const andWhere = vi.fn().mockReturnThis();
      const queryBuilderMock = {
        orderBy: vi.fn().mockReturnThis(),
        andWhere,
        getMany: vi.fn().mockResolvedValue([baseTable]),
      };
      repository.createQueryBuilder!.mockReturnValue(queryBuilderMock);

      await service.findAll({}); // sin filtros

      // Si no llegó ningún filtro, andWhere() nunca debió llamarse
      expect(andWhere).not.toHaveBeenCalled();
    });
  });
});
