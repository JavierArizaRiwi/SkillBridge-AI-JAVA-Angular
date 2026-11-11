// Prueba findAvailableTables SIN base de datos: el query builder es un mock
// encadenable. El solapamiento real (SQL) se verifica en el e2e de disponibilidad.

import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Table } from '../tables/entities/table.entity.js';
import { TableStatus, TableZone } from '../tables/enums/index.js';
import { Reservation } from './entities/reservation.entity.js';
import { ReservationStatus } from './enums/index.js';
import { ReservationsService } from './reservations.service.js';

const table = (number: number, capacity: number): Table => ({
  id: `table-${number}`,
  number,
  capacity,
  zone: TableZone.TERRACE,
  status: TableStatus.AVAILABLE,
  createdAt: new Date(),
  updatedAt: new Date(),
});

// Query builder falso: cada método devuelve el mismo objeto para encadenar
function mockQueryBuilder(result: Table[]) {
  const qb: Record<string, Mock> = {};
  for (const method of [
    'where',
    'andWhere',
    'setParameters',
    'orderBy',
    'addOrderBy',
  ]) {
    qb[method] = vi.fn(() => qb);
  }
  qb.getMany = vi.fn().mockResolvedValue(result);
  return qb;
}

function mockManager(qb: Record<string, Mock>) {
  return {
    getRepository: vi.fn(() => ({ createQueryBuilder: vi.fn(() => qb) })),
  };
}

const query = { date: '2026-12-20', time: '19:00', guests: 4 };

describe('ReservationsService.findAvailableTables', () => {
  let service: ReservationsService;
  let qb: Record<string, Mock>;
  let manager: ReturnType<typeof mockManager>;

  beforeEach(async () => {
    qb = mockQueryBuilder([table(1, 4), table(2, 6)]);
    manager = mockManager(qb);

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ReservationsService,
        { provide: getRepositoryToken(Reservation), useValue: { manager } },
        { provide: ConfigService, useValue: { getOrThrow: vi.fn(() => 120) } },
      ],
    }).compile();

    service = module.get(ReservationsService);
  });

  it('devuelve las mesas con su resumen y available: true', async () => {
    const result = await service.findAvailableTables(query);

    expect(result).toEqual({
      available: true,
      tables: [
        { id: 'table-1', number: 1, zone: TableZone.TERRACE, capacity: 4 },
        { id: 'table-2', number: 2, zone: TableZone.TERRACE, capacity: 6 },
      ],
    });
  });

  it('sin mesas libres responde available: false y lista vacía', async () => {
    qb.getMany.mockResolvedValue([]);

    await expect(service.findAvailableTables(query)).resolves.toEqual({
      available: false,
      tables: [],
    });
  });

  it('excluye OUT_OF_SERVICE y exige capacidad suficiente (RN-039, RN-041)', async () => {
    await service.findAvailableTables(query);

    expect(qb.where).toHaveBeenCalledWith('t.status != :outOfService', {
      outOfService: TableStatus.OUT_OF_SERVICE,
    });
    expect(qb.andWhere).toHaveBeenCalledWith('t.capacity >= :guests', {
      guests: 4,
    });
  });

  it('usa la duración de la configuración y solo los estados que bloquean (D-001)', async () => {
    await service.findAvailableTables(query);

    expect(qb.setParameters).toHaveBeenCalledWith({
      blocking: [
        ReservationStatus.PENDING,
        ReservationStatus.CONFIRMED,
        ReservationStatus.CHECKED_IN,
      ],
      start: '2026-12-20 19:00',
      duration: 120,
    });
  });

  it('ordena por capacidad ascendente para asignar la mesa más ajustada', async () => {
    await service.findAvailableTables(query);

    expect(qb.orderBy).toHaveBeenCalledWith('t.capacity', 'ASC');
    expect(qb.addOrderBy).toHaveBeenCalledWith('t.number', 'ASC');
  });

  it('usa el manager recibido para correr dentro de una transacción (HU-007)', async () => {
    const otherQb = mockQueryBuilder([]);
    const transactionManager = mockManager(otherQb);

    await service.findAvailableTables(query, transactionManager as never);

    expect(transactionManager.getRepository).toHaveBeenCalledWith(Table);
    expect(manager.getRepository).not.toHaveBeenCalled();
  });
});
