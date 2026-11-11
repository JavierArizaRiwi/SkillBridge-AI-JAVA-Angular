import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { TableStatus, TableZone } from '../tables/enums/index.js';
import { Reservation } from './entities/reservation.entity.js';
import { ReservationStatus } from './enums/index.js';
import { ReservationsService } from './reservations.service.js';

const ID = '11111111-1111-1111-1111-111111111111';

// Reserva de prueba con su mesa. Cada test cambia solo lo que necesita.
function buildReservation(
  overrides: Record<string, unknown> = {},
): Reservation {
  return {
    id: ID,
    customerName: 'Carlos Pérez',
    phone: '3001234567',
    email: 'carlos@example.com',
    date: '2099-06-01',
    time: '19:00:00',
    guests: 4,
    status: ReservationStatus.PENDING,
    tableId: 'table-1',
    table: {
      id: 'table-1',
      number: 1,
      zone: TableZone.INTERIOR,
      capacity: 4,
      status: TableStatus.AVAILABLE,
    },
    confirmedAt: null,
    ...overrides,
  } as unknown as Reservation;
}

describe('ReservationsService.confirm', () => {
  let service: ReservationsService;
  let repository: {
    findOne: ReturnType<typeof vi.fn>;
    save: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    // El repositorio simulado: findOne lo configura cada test y save devuelve
    // lo mismo que recibe, como haría la base de datos.
    repository = {
      findOne: vi.fn(),
      save: vi.fn(async (reservation: Reservation) => reservation),
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        ReservationsService,
        { provide: getRepositoryToken(Reservation), useValue: repository },
      ],
    })
      // Otras dependencias del servicio (que confirm no usa) se simulan vacías.
      .useMocker(() => ({}))
      .compile();

    service = moduleRef.get(ReservationsService);
  });

  it('confirma una reserva PENDING y registra confirmedAt', async () => {
    repository.findOne.mockResolvedValue(buildReservation());

    const result = await service.confirm(ID);

    expect(result.status).toBe(ReservationStatus.CONFIRMED);
    expect(result.confirmedAt).toBeInstanceOf(Date);
    expect(repository.save).toHaveBeenCalledTimes(1);
  });

  it('conserva la mesa asignada', async () => {
    repository.findOne.mockResolvedValue(buildReservation());

    const result = await service.confirm(ID);

    expect(result.table?.id).toBe('table-1');
    expect(repository.save).toHaveBeenCalledWith(
      expect.objectContaining({
        tableId: 'table-1',
        status: ReservationStatus.CONFIRMED,
      }),
    );
  });

  it('lanza 404 si la reserva no existe (RN-065)', async () => {
    repository.findOne.mockResolvedValue(null);

    await expect(service.confirm(ID)).rejects.toThrow(EntityNotFoundException);
    expect(repository.save).not.toHaveBeenCalled();
  });

  it.each([
    ReservationStatus.CONFIRMED,
    ReservationStatus.CHECKED_IN,
    ReservationStatus.CANCELLED,
    ReservationStatus.NO_SHOW,
    ReservationStatus.COMPLETED,
  ])('rechaza con 409 confirmar una reserva %s', async (status) => {
    repository.findOne.mockResolvedValue(buildReservation({ status }));

    await expect(service.confirm(ID)).rejects.toThrow(BusinessRuleException);
    expect(repository.save).not.toHaveBeenCalled();
  });

  it('rechaza con 409 si la mesa asignada está fuera de servicio (RN-068)', async () => {
    repository.findOne.mockResolvedValue(
      buildReservation({
        table: {
          id: 'table-1',
          number: 1,
          zone: TableZone.INTERIOR,
          capacity: 4,
          status: TableStatus.OUT_OF_SERVICE,
        },
      }),
    );

    const error = await service.confirm(ID).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BusinessRuleException);
    expect((error as BusinessRuleException).getResponse()).toMatchObject({
      rule: 'RN-068',
    });
    expect(repository.save).not.toHaveBeenCalled();
  });

  it('rechaza con 409 si la reserva no tiene mesa (RN-068)', async () => {
    repository.findOne.mockResolvedValue(
      buildReservation({ table: null, tableId: null }),
    );

    const error = await service.confirm(ID).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BusinessRuleException);
    expect((error as BusinessRuleException).getResponse()).toMatchObject({
      rule: 'RN-068',
    });
    expect(repository.save).not.toHaveBeenCalled();
  });
});
