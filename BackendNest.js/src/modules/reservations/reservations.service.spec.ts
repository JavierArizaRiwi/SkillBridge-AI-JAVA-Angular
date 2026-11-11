// Prueba ReservationsService SIN base de datos: el repositorio es un mock
// que responde lo que cada test le indique (mismo enfoque que tables).

import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { EntityNotFoundException } from '../../common/exceptions/index.js';
import { Table } from '../tables/entities/table.entity.js';
import { TableStatus, TableZone } from '../tables/enums/index.js';
import { Reservation } from './entities/reservation.entity.js';
import { ReservationStatus } from './enums/index.js';
import { ReservationsService } from './reservations.service.js';

const mockRepository = () => ({
  findOne: vi.fn(),
  createQueryBuilder: vi.fn(),
});

type MockRepo = Partial<Record<keyof Repository<Reservation>, Mock>>;

const table: Table = {
  id: 'table-1',
  number: 5,
  capacity: 4,
  zone: TableZone.INTERIOR,
  status: TableStatus.AVAILABLE,
  createdAt: new Date(),
  updatedAt: new Date(),
};

// Una reserva tal como la devolvería PostgreSQL (la hora viene con segundos)
const reservation: Reservation = {
  id: 'reservation-1',
  customerName: 'Carlos Pérez',
  phone: '3001234567',
  email: 'carlos@example.com',
  date: '2026-10-10',
  time: '19:00:00',
  guests: 4,
  status: ReservationStatus.CONFIRMED,
  tableId: table.id,
  table,
  confirmedAt: new Date('2026-10-08T15:00:00Z'),
  checkedInAt: null,
  cancelledAt: null,
  noShowAt: null,
  completedAt: null,
  createdAt: new Date(),
  updatedAt: new Date(),
};

describe('ReservationsService', () => {
  let service: ReservationsService;
  let repository: MockRepo;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ReservationsService,
        {
          provide: getRepositoryToken(Reservation),
          useFactory: mockRepository,
        },
        // ConfigService mockeado: getOrThrow('reservations.durationMinutes') → 120
        { provide: ConfigService, useValue: { getOrThrow: vi.fn(() => 120) } },
      ],
    }).compile();

    service = module.get(ReservationsService);
    repository = module.get(getRepositoryToken(Reservation));
  });

  describe('findOne', () => {
    it('devuelve la reserva con su mesa y la hora en HH:MM', async () => {
      repository.findOne!.mockResolvedValue(reservation);

      const result = await service.findOne('reservation-1');

      expect(repository.findOne).toHaveBeenCalledWith({
        where: { id: 'reservation-1' },
        relations: { table: true },
      });
      expect(result.time).toBe('19:00');
      expect(result.status).toBe(ReservationStatus.CONFIRMED);
      expect(result.table).toEqual({
        id: 'table-1',
        number: 5,
        zone: TableZone.INTERIOR,
        capacity: 4,
      });
    });

    it('devuelve table: null si la reserva no tiene mesa (RN-050)', async () => {
      repository.findOne!.mockResolvedValue({
        ...reservation,
        tableId: null,
        table: null,
      });

      const result = await service.findOne('reservation-1');

      expect(result.table).toBeNull();
    });

    it('lanza EntityNotFoundException si no existe (RN-048)', async () => {
      repository.findOne!.mockResolvedValue(null);

      await expect(service.findOne('no-existe')).rejects.toThrow(
        EntityNotFoundException,
      );
    });
  });

  describe('findAll', () => {
    // Simula el query builder: cada método devuelve el mismo objeto para
    // poder encadenarlos, y getMany resuelve con las reservas indicadas.
    function mockQueryBuilder(result: Reservation[]) {
      const qb = {
        leftJoinAndSelect: vi.fn().mockReturnThis(),
        orderBy: vi.fn().mockReturnThis(),
        addOrderBy: vi.fn().mockReturnThis(),
        skip: vi.fn().mockReturnThis(),
        take: vi.fn().mockReturnThis(),
        andWhere: vi.fn().mockReturnThis(),
        getMany: vi.fn().mockResolvedValue(result),
      };
      repository.createQueryBuilder!.mockReturnValue(qb);
      return qb;
    }

    it('trae la mesa, ordena por fecha y hora y pagina', async () => {
      const qb = mockQueryBuilder([reservation]);

      const result = await service.findAll({ page: 2, limit: 10 });

      expect(qb.leftJoinAndSelect).toHaveBeenCalledWith(
        'reservation.table',
        'table',
      );
      expect(qb.orderBy).toHaveBeenCalledWith('reservation.date', 'ASC');
      expect(qb.addOrderBy).toHaveBeenCalledWith('reservation.time', 'ASC');
      expect(qb.addOrderBy).toHaveBeenCalledWith('reservation.id', 'ASC');
      expect(qb.skip).toHaveBeenCalledWith(10); // página 2 de a 10: salta 10
      expect(qb.take).toHaveBeenCalledWith(10);
      expect(result).toHaveLength(1);
      expect(result[0].time).toBe('19:00');
    });

    it('sin filtros no agrega condiciones: no oculta canceladas ni completadas (RN-051)', async () => {
      const qb = mockQueryBuilder([]);

      await service.findAll({ page: 1, limit: 10 });

      expect(qb.andWhere).not.toHaveBeenCalled();
    });

    it('aplica los filtros de fecha, estado y mesa que lleguen', async () => {
      const qb = mockQueryBuilder([]);

      await service.findAll({
        page: 1,
        limit: 10,
        date: '2026-10-10',
        status: ReservationStatus.CANCELLED,
        tableId: 'table-1',
      });

      expect(qb.andWhere).toHaveBeenCalledWith('reservation.date = :date', {
        date: '2026-10-10',
      });
      expect(qb.andWhere).toHaveBeenCalledWith('reservation.status = :status', {
        status: ReservationStatus.CANCELLED,
      });
      expect(qb.andWhere).toHaveBeenCalledWith(
        'reservation.tableId = :tableId',
        { tableId: 'table-1' },
      );
    });
  });
});
