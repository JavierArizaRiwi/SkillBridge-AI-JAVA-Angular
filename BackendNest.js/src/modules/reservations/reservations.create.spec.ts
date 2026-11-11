// Prueba create SIN base de datos: la transacción y findAvailableTables son
// mocks. La concurrencia real (bloqueo de la fila) se verifica en el e2e.

import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { getRepositoryToken } from '@nestjs/typeorm';
import { BusinessRuleException } from '../../common/exceptions/index.js';
import { Table } from '../tables/entities/table.entity.js';
import { TableZone } from '../tables/enums/index.js';
import type { CreateReservationDto } from './dto/create-reservation.dto.js';
import type { ReservationResponseDto } from './dto/reservation-response.dto.js';
import { Reservation } from './entities/reservation.entity.js';
import { ReservationStatus } from './enums/index.js';
import { ReservationsService } from './reservations.service.js';

const dto: CreateReservationDto = {
  customerName: 'Carlos Pérez',
  phone: '3001234567',
  email: 'carlos@example.com',
  date: '2026-12-20',
  time: '19:00',
  guests: 4,
};

const candidate = (number: number, capacity: number) => ({
  id: `table-${number}`,
  number,
  capacity,
  zone: TableZone.INTERIOR,
});

describe('ReservationsService.create', () => {
  let service: ReservationsService;
  let tableRepo: { findOne: Mock };
  let reservationRepo: { save: Mock };
  let manager: { getRepository: Mock };
  let findAvailableTables: Mock;

  beforeEach(async () => {
    tableRepo = { findOne: vi.fn() };
    reservationRepo = {
      save: vi.fn((data: object) => ({ id: 'reservation-1', ...data })),
    };
    manager = {
      getRepository: vi.fn((entity) =>
        entity === Table ? tableRepo : reservationRepo,
      ),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ReservationsService,
        {
          provide: getRepositoryToken(Reservation),
          // transaction ejecuta el callback con nuestro manager falso
          useValue: {
            manager: { transaction: vi.fn((work) => work(manager)) },
          },
        },
        { provide: ConfigService, useValue: { getOrThrow: vi.fn(() => 120) } },
      ],
    }).compile();

    service = module.get(ReservationsService);
    findAvailableTables = vi.spyOn(service, 'findAvailableTables') as Mock;
    vi.spyOn(service, 'findOne').mockResolvedValue({
      id: 'reservation-1',
    } as ReservationResponseDto);
  });

  it('guarda la reserva PENDING en la mesa más ajustada (RN-046)', async () => {
    const tables = [candidate(1, 4), candidate(2, 6)];
    findAvailableTables.mockResolvedValue({ available: true, tables });

    const result = await service.create(dto);

    expect(reservationRepo.save).toHaveBeenCalledWith({
      ...dto,
      status: ReservationStatus.PENDING,
      tableId: 'table-1',
    });
    expect(service.findOne).toHaveBeenCalledWith('reservation-1');
    expect(result.id).toBe('reservation-1');
  });

  it('bloquea la fila de la mesa antes de volver a comprobarla (RN-045)', async () => {
    findAvailableTables.mockResolvedValue({
      available: true,
      tables: [candidate(1, 4)],
    });

    await service.create(dto);

    expect(tableRepo.findOne).toHaveBeenCalledWith({
      where: { id: 'table-1' },
      lock: { mode: 'pessimistic_write' },
    });
    // dentro de la transacción: siempre con el manager recibido
    expect(findAvailableTables).toHaveBeenCalledWith(dto, manager, undefined);
  });

  it('si otra solicitud tomó la primera mesa, usa la siguiente', async () => {
    findAvailableTables
      .mockResolvedValueOnce({
        available: true,
        tables: [candidate(1, 4), candidate(2, 6)],
      })
      // al re-verificar, la mesa 1 ya no está libre
      .mockResolvedValue({ available: true, tables: [candidate(2, 6)] });

    await service.create(dto);

    expect(reservationRepo.save).toHaveBeenCalledWith(
      expect.objectContaining({ tableId: 'table-2' }),
    );
  });

  it('sin mesas disponibles lanza 409 con RN-044 y no guarda nada', async () => {
    findAvailableTables.mockResolvedValue({ available: false, tables: [] });

    const error = await service.create(dto).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BusinessRuleException);
    expect((error as BusinessRuleException).getResponse()).toMatchObject({
      rule: 'RN-044',
    });
    expect(reservationRepo.save).not.toHaveBeenCalled();
  });
});
