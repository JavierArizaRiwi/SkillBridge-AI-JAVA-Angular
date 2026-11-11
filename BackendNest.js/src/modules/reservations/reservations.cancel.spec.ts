// Prueba cancel SIN base de datos: el repositorio es un mock.

import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test, TestingModule } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Reservation } from './entities/reservation.entity.js';
import { ReservationStatus } from './enums/index.js';
import { ReservationsService } from './reservations.service.js';

const reservation = (status: ReservationStatus): Reservation => ({
  id: 'reservation-1',
  customerName: 'Carlos Pérez',
  phone: '3001234567',
  email: 'carlos@example.com',
  date: '2026-12-20',
  time: '19:00:00',
  guests: 4,
  status,
  tableId: null,
  table: null,
  confirmedAt: null,
  checkedInAt: null,
  cancelledAt: null,
  noShowAt: null,
  completedAt: null,
  createdAt: new Date(),
  updatedAt: new Date(),
});

async function ruleOf(promise: Promise<unknown>) {
  const error = await promise.catch((e: unknown) => e);
  expect(error).toBeInstanceOf(BusinessRuleException);
  return ((error as BusinessRuleException).getResponse() as { rule: string })
    .rule;
}

describe('ReservationsService.cancel', () => {
  let service: ReservationsService;
  let repository: { findOne: Mock; save: Mock };

  beforeEach(async () => {
    repository = {
      findOne: vi.fn(),
      save: vi.fn((entity: Reservation) => entity),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ReservationsService,
        { provide: getRepositoryToken(Reservation), useValue: repository },
        { provide: ConfigService, useValue: { getOrThrow: vi.fn(() => 120) } },
      ],
    }).compile();

    service = module.get(ReservationsService);
  });

  it.each([ReservationStatus.PENDING, ReservationStatus.CONFIRMED])(
    'cancela una reserva %s y registra cancelledAt (RN-064)',
    async (status) => {
      repository.findOne.mockResolvedValue(reservation(status));

      const result = await service.cancel('reservation-1');

      expect(result.status).toBe(ReservationStatus.CANCELLED);
      expect(result.cancelledAt).toBeInstanceOf(Date);
      expect(repository.save).toHaveBeenCalledWith(
        expect.objectContaining({ status: ReservationStatus.CANCELLED }),
      );
    },
  );

  it('una reserva inexistente lanza EntityNotFoundException (RN-059)', async () => {
    repository.findOne.mockResolvedValue(null);

    await expect(service.cancel('nope')).rejects.toBeInstanceOf(
      EntityNotFoundException,
    );
  });

  it('una reserva ya cancelada responde 409 con RN-060', async () => {
    repository.findOne.mockResolvedValue(
      reservation(ReservationStatus.CANCELLED),
    );

    expect(await ruleOf(service.cancel('reservation-1'))).toBe('RN-060');
  });

  it('una reserva con check-in responde 409 con RN-061', async () => {
    repository.findOne.mockResolvedValue(
      reservation(ReservationStatus.CHECKED_IN),
    );

    expect(await ruleOf(service.cancel('reservation-1'))).toBe('RN-061');
  });

  it.each([ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW])(
    'una reserva %s (estado final) responde 409 con RN-057',
    async (status) => {
      repository.findOne.mockResolvedValue(reservation(status));

      expect(await ruleOf(service.cancel('reservation-1'))).toBe('RN-057');
    },
  );

  it('si no se puede cancelar, no guarda nada (RN-062)', async () => {
    repository.findOne.mockResolvedValue(
      reservation(ReservationStatus.CHECKED_IN),
    );

    await service.cancel('reservation-1').catch(() => undefined);

    expect(repository.save).not.toHaveBeenCalled();
  });
});
