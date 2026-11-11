import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Table } from '../tables/entities/table.entity.js';
import { TableStatus } from '../tables/enums/index.js';
import { Reservation } from './entities/reservation.entity.js';
import { ReservationStatus } from './enums/index.js';
import { ReservationsService } from './reservations.service.js';

const ID = '11111111-1111-1111-1111-111111111111';
const TABLE_ID = '22222222-2222-2222-2222-222222222222';

// Reserva y mesa de prueba. Cada test cambia solo lo que necesita.
function buildReservation(
  overrides: Record<string, unknown> = {},
): Reservation {
  return {
    id: ID,
    status: ReservationStatus.CONFIRMED,
    tableId: TABLE_ID,
    checkedInAt: null,
    ...overrides,
  } as unknown as Reservation;
}

function buildTable(overrides: Record<string, unknown> = {}): Table {
  return {
    id: TABLE_ID,
    number: 1,
    capacity: 4,
    status: TableStatus.AVAILABLE,
    ...overrides,
  } as unknown as Table;
}

describe('ReservationsService.checkIn', () => {
  let service: ReservationsService;
  let reservations: {
    findOne: ReturnType<typeof vi.fn>;
    save: ReturnType<typeof vi.fn>;
  };
  let tables: {
    findOne: ReturnType<typeof vi.fn>;
    save: ReturnType<typeof vi.fn>;
  };

  // Ejecuta checkIn y devuelve el error que lanzó (o undefined si no lanzó)
  const captureError = (): Promise<unknown> =>
    service.checkIn(ID).then(
      () => undefined,
      (error: unknown) => error,
    );

  beforeEach(async () => {
    // Repositorios simulados: save devuelve lo mismo que recibe
    reservations = {
      findOne: vi.fn(),
      save: vi.fn(async (reservation: Reservation) => reservation),
    };
    tables = {
      findOne: vi.fn(),
      save: vi.fn(async (table: Table) => table),
    };

    // El manager simulado entrega el repositorio que corresponde a cada entidad
    const manager = {
      getRepository: vi.fn((entity: unknown) =>
        entity === Table ? tables : reservations,
      ),
    };

    // La transacción simulada solo ejecuta la función que recibe con el manager
    const transaction = vi.fn((callback: (manager: unknown) => Promise<void>) =>
      callback(manager),
    );

    const moduleRef = await Test.createTestingModule({
      providers: [
        ReservationsService,
        {
          provide: getRepositoryToken(Reservation),
          useValue: { manager: { transaction } },
        },
      ],
    })
      // Otras dependencias del servicio (que checkIn no usa) se simulan vacías
      .useMocker(() => ({}))
      .compile();

    service = moduleRef.get(ReservationsService);

    // Al terminar, checkIn devuelve findOne(id); aquí no es lo que se prueba
    vi.spyOn(service, 'findOne').mockResolvedValue({ id: ID } as never);
  });

  it('registra el check-in: reserva CHECKED_IN con checkedInAt y mesa OCCUPIED', async () => {
    reservations.findOne.mockResolvedValue(buildReservation());
    tables.findOne.mockResolvedValue(buildTable());

    await service.checkIn(ID);

    expect(reservations.save).toHaveBeenCalledWith(
      expect.objectContaining({
        status: ReservationStatus.CHECKED_IN,
        checkedInAt: expect.any(Date),
      }),
    );
    expect(tables.save).toHaveBeenCalledWith(
      expect.objectContaining({ status: TableStatus.OCCUPIED }),
    );
  });

  it('lanza 404 si la reserva no existe (RN-070)', async () => {
    reservations.findOne.mockResolvedValue(null);

    await expect(service.checkIn(ID)).rejects.toThrow(EntityNotFoundException);
    expect(reservations.save).not.toHaveBeenCalled();
    expect(tables.save).not.toHaveBeenCalled();
  });

  it.each([
    ReservationStatus.PENDING,
    ReservationStatus.CHECKED_IN,
    ReservationStatus.CANCELLED,
    ReservationStatus.NO_SHOW,
    ReservationStatus.COMPLETED,
  ])('rechaza con 409 el check-in de una reserva %s', async (status) => {
    reservations.findOne.mockResolvedValue(buildReservation({ status }));

    await expect(service.checkIn(ID)).rejects.toThrow(BusinessRuleException);
    expect(tables.findOne).not.toHaveBeenCalled();
    expect(reservations.save).not.toHaveBeenCalled();
    expect(tables.save).not.toHaveBeenCalled();
  });

  it('rechaza con 409 si la reserva no tiene mesa asignada (RN-072)', async () => {
    reservations.findOne.mockResolvedValue(buildReservation({ tableId: null }));

    const error = await captureError();

    expect(error).toBeInstanceOf(BusinessRuleException);
    expect((error as BusinessRuleException).getResponse()).toMatchObject({
      rule: 'RN-072',
    });
    expect(reservations.save).not.toHaveBeenCalled();
    expect(tables.save).not.toHaveBeenCalled();
  });

  it('rechaza con 409 si la mesa ya está ocupada', async () => {
    reservations.findOne.mockResolvedValue(buildReservation());
    tables.findOne.mockResolvedValue(
      buildTable({ status: TableStatus.OCCUPIED }),
    );

    await expect(service.checkIn(ID)).rejects.toThrow(BusinessRuleException);
    expect(reservations.save).not.toHaveBeenCalled();
    expect(tables.save).not.toHaveBeenCalled();
  });

  it('si falla al guardar la mesa, el error se propaga para que la transacción haga rollback', async () => {
    reservations.findOne.mockResolvedValue(buildReservation());
    tables.findOne.mockResolvedValue(buildTable());
    tables.save.mockRejectedValue(new Error('fallo al guardar la mesa'));

    await expect(service.checkIn(ID)).rejects.toThrow(
      'fallo al guardar la mesa',
    );
  });
});
