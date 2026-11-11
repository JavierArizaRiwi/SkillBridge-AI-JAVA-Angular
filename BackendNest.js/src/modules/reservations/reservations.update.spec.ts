import { BadRequestException } from '@nestjs/common';
import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Table } from '../tables/entities/table.entity.js';
import { TableZone } from '../tables/enums/index.js';
import type { ReservationResponseDto } from './dto/reservation-response.dto.js';
import type { UpdateReservationDto } from './dto/update-reservation.dto.js';
import { Reservation } from './entities/reservation.entity.js';
import { ReservationStatus } from './enums/index.js';
import { ReservationsService } from './reservations.service.js';

const existingReservation = (
  status = ReservationStatus.PENDING,
): Reservation => ({
  id: 'reservation-1',
  customerName: 'Carlos Pérez',
  phone: '3001234567',
  email: 'carlos@example.com',
  date: '2030-12-20',
  time: '19:00:00',
  guests: 4,
  status,
  tableId: 'table-1',
  table: {
    id: 'table-1',
    number: 1,
    capacity: 4,
    zone: TableZone.INTERIOR,
  } as Table,
  confirmedAt: null,
  checkedInAt: null,
  cancelledAt: null,
  noShowAt: null,
  completedAt: null,
  createdAt: new Date(),
  updatedAt: new Date(),
});

const candidate = (number: number, capacity: number) => ({
  id: `table-${number}`,
  number,
  capacity,
  zone: TableZone.INTERIOR,
});

describe('ReservationsService.update', () => {
  let service: ReservationsService;
  let tableRepo: { findOne: Mock };
  let reservationRepo: { findOne: Mock; save: Mock; merge: Mock };
  let manager: { getRepository: Mock };
  let findAvailableTables: Mock;

  beforeEach(async () => {
    tableRepo = { findOne: vi.fn() };
    reservationRepo = {
      findOne: vi.fn(),
      save: vi.fn((data: object) => data),
      merge: vi.fn((entity: object, ...partials: object[]) =>
        Object.assign(entity, ...partials),
      ),
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
          useValue: {
            manager: { transaction: vi.fn((work) => work(manager)) },
            findOne: vi.fn(),
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

  it('contacto sin tocar la mesa: no consulta disponibilidad ni reasigna', async () => {
    const reservation = existingReservation();
    reservationRepo.findOne.mockResolvedValue(reservation);

    const dto: UpdateReservationDto = {
      customerName: 'Carlos M. Pérez',
      phone: '3009876543',
    };

    const result = await service.update('reservation-1', dto);

    expect(findAvailableTables).not.toHaveBeenCalled();
    expect(reservationRepo.save).toHaveBeenCalledWith(
      expect.objectContaining({
        customerName: 'Carlos M. Pérez',
        phone: '3009876543',
        tableId: 'table-1',
      }),
    );
    expect(result.id).toBe('reservation-1');
  });

  it('mantiene la mesa si sigue libre y con capacidad suficiente', async () => {
    const reservation = existingReservation();
    reservationRepo.findOne.mockResolvedValue(reservation);
    tableRepo.findOne.mockResolvedValue({ id: 'table-1' }); // simula el lock de la mesa actual
    // mismo valor en ambas llamadas: primer check y reverificación tras lock
    findAvailableTables.mockResolvedValue({
      available: true,
      tables: [candidate(1, 4), candidate(2, 6)],
    });

    const dto: UpdateReservationDto = { time: '20:00' };
    await service.update('reservation-1', dto);

    // Se llama dos veces: primer check + reverificación tras pessimistic_write (RN-045)
    expect(findAvailableTables).toHaveBeenCalledTimes(2);
    expect(findAvailableTables).toHaveBeenCalledWith(
      { date: '2030-12-20', time: '20:00', guests: 4 },
      manager,
      'reservation-1',
    );
    // La mesa actual se bloquea antes de confirmar que se conserva
    expect(tableRepo.findOne).toHaveBeenCalledWith({
      where: { id: 'table-1' },
      lock: { mode: 'pessimistic_write' },
    });
    expect(reservationRepo.save).toHaveBeenCalledWith(
      expect.objectContaining({
        time: '20:00',
        tableId: 'table-1',
      }),
    );
  });

  it('reasigna mesa si la actual ya no está disponible o no cumple capacidad (RN-056)', async () => {
    const reservation = existingReservation();
    reservationRepo.findOne.mockResolvedValue(reservation);
    // table-1 no está disponible, solo table-2
    findAvailableTables.mockResolvedValue({
      available: true,
      tables: [candidate(2, 6)],
    });
    tableRepo.findOne.mockResolvedValue({ id: 'table-2' });

    const dto: UpdateReservationDto = { guests: 6 };
    await service.update('reservation-1', dto);

    expect(tableRepo.findOne).toHaveBeenCalledWith({
      where: { id: 'table-2' },
      lock: { mode: 'pessimistic_write' },
    });
    expect(reservationRepo.save).toHaveBeenCalledWith(
      expect.objectContaining({
        guests: 6,
        tableId: 'table-2',
      }),
    );
  });

  it('sin mesa disponible lanza 409 con RN-058 y no guarda nada', async () => {
    const reservation = existingReservation();
    reservationRepo.findOne.mockResolvedValue(reservation);
    findAvailableTables.mockResolvedValue({ available: false, tables: [] });

    const dto: UpdateReservationDto = { time: '20:00' };
    const error = await service
      .update('reservation-1', dto)
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BusinessRuleException);
    expect((error as BusinessRuleException).getResponse()).toMatchObject({
      rule: 'RN-058',
    });
    expect(reservationRepo.save).not.toHaveBeenCalled();
  });

  it.each([
    ReservationStatus.CHECKED_IN,
    ReservationStatus.CANCELLED,
    ReservationStatus.NO_SHOW,
    ReservationStatus.COMPLETED,
  ])('estado no modificable %s responde 409 con RN-057', async (status) => {
    reservationRepo.findOne.mockResolvedValue(existingReservation(status));

    const dto: UpdateReservationDto = { customerName: 'Nuevo Nombre' };
    const error = await service
      .update('reservation-1', dto)
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BusinessRuleException);
    expect((error as BusinessRuleException).getResponse()).toMatchObject({
      rule: 'RN-057',
    });
    expect(reservationRepo.save).not.toHaveBeenCalled();
  });

  it('reasigna si la mesa actual es arrebatada entre el primer check y el bloqueo (RN-045)', async () => {
    const reservation = existingReservation();
    reservationRepo.findOne.mockResolvedValue(reservation);
    // Secuencia de llamadas a findAvailableTables:
    //   1ª (update): table-1 parece libre → keepsTable = true
    //   2ª (update tras lock): mesa ya tomada → keepsTable = false
    //   3ª (lockFirst, 1er check): table-2 disponible
    //   4ª (lockFirst, tras lock de table-2): table-2 sigue disponible → retorna 'table-2'
    findAvailableTables
      .mockResolvedValueOnce({ available: true, tables: [candidate(1, 4)] })
      .mockResolvedValueOnce({ available: false, tables: [] })
      .mockResolvedValueOnce({ available: true, tables: [candidate(2, 6)] })
      .mockResolvedValueOnce({ available: true, tables: [candidate(2, 6)] });
    // Locks: primero table-1 (en update), luego table-2 (en lockFirst)
    tableRepo.findOne
      .mockResolvedValueOnce({ id: 'table-1' })
      .mockResolvedValueOnce({ id: 'table-2' });

    const dto: UpdateReservationDto = { time: '20:00' };
    await service.update('reservation-1', dto);

    expect(reservationRepo.save).toHaveBeenCalledWith(
      expect.objectContaining({ tableId: 'table-2' }),
    );
  });

  it('fecha en el pasado lanza BadRequestException con RN-053 y no guarda nada', async () => {
    const reservation = existingReservation();
    reservationRepo.findOne.mockResolvedValue(reservation);

    // 2020-01-01 es pasado: isFutureInRestaurantTz retorna false
    const dto: UpdateReservationDto = { date: '2020-01-01', time: '19:00' };
    const error = await service
      .update('reservation-1', dto)
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BadRequestException);
    expect((error as BadRequestException).getResponse()).toMatchObject({
      rule: 'RN-053',
    });
    expect(reservationRepo.save).not.toHaveBeenCalled();
  });

  it('inexistente responde 404 EntityNotFoundException (RN-052)', async () => {
    reservationRepo.findOne.mockResolvedValue(null);

    await expect(
      service.update('no-existe', { customerName: 'Cualquiera' }),
    ).rejects.toBeInstanceOf(EntityNotFoundException);

    expect(reservationRepo.save).not.toHaveBeenCalled();
  });
});
