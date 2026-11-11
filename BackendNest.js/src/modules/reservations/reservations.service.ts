// Reglas de negocio de las reservas. Cada historia añade aquí sus métodos:
// findAvailableTables (HU-006), create (HU-007), findAll/findOne (HU-008)...
// Los parámetros de duración y tolerancia se leen de ConfigService
// (reservations.durationMinutes, reservations.noShowToleranceMinutes), nunca fijos.

import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { EntityManager, Repository } from 'typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { isFutureInRestaurantTz } from '../../common/validators/index.js';
import { FilterReservationsDto } from './dto/filter-reservations.dto.js';
import { ReservationResponseDto } from './dto/reservation-response.dto.js';
import { AvailabilityQueryDto } from './dto/availability-query.dto.js';
import { AvailabilityResponseDto } from './dto/availability-response.dto.js';
import { CreateReservationDto } from './dto/create-reservation.dto.js';
import { UpdateReservationDto } from './dto/update-reservation.dto.js';
import { Reservation } from './entities/reservation.entity.js';
import {
  BLOCKING_STATUSES,
  ReservationStatus,
  assertTransition,
} from './enums/index.js';
import { Table } from '../tables/entities/table.entity.js';
import { TableStatus } from '../tables/enums/index.js';
import { ConfigService } from '@nestjs/config';

const NOT_MODIFIABLE = [
  ReservationStatus.CHECKED_IN, // cliente ya sentado (RN-057)
  ReservationStatus.CANCELLED,
  ReservationStatus.NO_SHOW,
  ReservationStatus.COMPLETED,
];

@Injectable()
export class ReservationsService {
  constructor(
    @InjectRepository(Reservation)
    private readonly reservationRepository: Repository<Reservation>,
    private readonly config: ConfigService,
  ) {}

  // HU-006: devuelve las mesas con capacidad >= guests que no tengan ninguna
  // reserva activa que se solape con la ventana [time, time + durationMinutes).
  // El manager por defecto sale del reservationRepository, lo que permite a
  // Dilant llamar a este método dentro de una transacción ya abierta.
  async findAvailableTables(
    query: AvailabilityQueryDto,
    manager: EntityManager = this.reservationRepository.manager,
    excludeReservationId?: string,
  ): Promise<AvailabilityResponseDto> {
    // Inicio de la ventana solicitada como string 'YYYY-MM-DD HH:mm'
    const start = `${query.date} ${query.time}`;
    // Duración en minutos leída de config (nunca hardcodeada)
    const duration = this.config.getOrThrow<number>(
      'reservations.durationMinutes',
    );

    const tables = await manager
      .getRepository(Table)
      .createQueryBuilder('t')
      // Excluye solo OUT_OF_SERVICE: OCCUPIED es estado presente,
      // no impide reservar la mesa para el futuro
      .where('t.status != :outOfService', {
        outOfService: TableStatus.OUT_OF_SERVICE,
      })
      // La mesa debe tener capacidad suficiente
      .andWhere('t.capacity >= :guests', { guests: query.guests })
      // NOT EXISTS: no existe reserva bloqueante que se solape con la ventana
      .andWhere((qb) => {
        const conflict = qb
          .subQuery()
          .select('1')
          .from(Reservation, 'r')
          .where('r.tableId = t.id')
          // Solo reservas activas bloquean (PENDING, CONFIRMED, CHECKED_IN)
          .andWhere('r.status IN (:...blocking)')
          // Solape D-001: [inicio, inicio + duración) de ambas reservas se cruzan
          .andWhere(
            '(r.date + r.time) < CAST(:start AS timestamp) + make_interval(mins => :duration)',
          )
          .andWhere(
            'CAST(:start AS timestamp) < (r.date + r.time) + make_interval(mins => :duration)',
          );

        if (excludeReservationId) {
          conflict.andWhere('r.id != :excludeReservationId');
        }

        return `NOT EXISTS ${conflict.getQuery()}`;
      })

      .setParameters({
        blocking: [...BLOCKING_STATUSES],
        start,
        duration,
        excludeReservationId,
      })
      // Capacidad ascendente: la primera mesa es la más ajustada (Dilant asigna esa)
      .orderBy('t.capacity', 'ASC')
      .addOrderBy('t.number', 'ASC')
      .getMany();

    return {
      available: tables.length > 0,
      tables: tables.map(({ id, number, zone, capacity }) => ({
        id,
        number,
        zone,
        capacity,
      })),
    };
  }

  private async lockFirstAvailableTable(
    query: AvailabilityQueryDto,
    manager: EntityManager,
    excludeReservationId?: string,
  ): Promise<string | null> {
    const { tables } = await this.findAvailableTables(
      query,
      manager,
      excludeReservationId,
    );
    for (const candidate of tables) {
      await manager.getRepository(Table).findOne({
        where: { id: candidate.id },
        lock: { mode: 'pessimistic_write' },
      });
      const again = await this.findAvailableTables(
        query,
        manager,
        excludeReservationId,
      );
      if (again.tables.some((t) => t.id === candidate.id)) return candidate.id;
    }
    return null;
  }

  // HU-007: registra la reserva en la mesa libre más ajustada, sin permitir
  // que dos solicitudes simultáneas se queden con la misma mesa (RN-045).
  async create(dto: CreateReservationDto): Promise<ReservationResponseDto> {
    const id = await this.reservationRepository.manager.transaction(
      async (manager) => {
        const tableId = await this.lockFirstAvailableTable(dto, manager);

        if (!tableId) {
          // Lanzar dentro de la transacción hace rollback automático
          throw new BusinessRuleException(
            'No hay mesas disponibles para esa fecha, hora y número de personas',
            'RN-044',
          );
        }

        const saved = await manager.getRepository(Reservation).save({
          ...dto,
          status: ReservationStatus.PENDING, // RN-046
          tableId,
        });
        return saved.id;
      },
    );

    // Misma respuesta que GET /reservations/:id, con la mesa asignada
    return this.findOne(id);
  }

  // HU-008: listado con filtros opcionales y paginación, por fecha y hora.
  // No excluye canceladas ni completadas (RN-051): solo filtra por estado
  // si lo piden en ?status=.
  async findAll(
    filters: FilterReservationsDto,
  ): Promise<ReservationResponseDto[]> {
    const query = this.reservationRepository
      .createQueryBuilder('reservation')
      // LEFT JOIN: también trae las reservas que aún no tienen mesa (RN-050)
      .leftJoinAndSelect('reservation.table', 'table')
      .orderBy('reservation.date', 'ASC')
      .addOrderBy('reservation.time', 'ASC')
      // desempate: sin él, la paginación puede repetir u omitir reservas de la misma hora
      .addOrderBy('reservation.id', 'ASC')
      .skip((filters.page - 1) * filters.limit)
      .take(filters.limit);

    if (filters.date) {
      query.andWhere('reservation.date = :date', { date: filters.date });
    }

    if (filters.status) {
      query.andWhere('reservation.status = :status', {
        status: filters.status,
      });
    }

    if (filters.tableId) {
      query.andWhere('reservation.tableId = :tableId', {
        tableId: filters.tableId,
      });
    }

    const reservations = await query.getMany();
    return reservations.map((reservation) => this.toResponse(reservation));
  }

  // HU-008: una reserva con su mesa. Si no existe, 404 (RN-048).
  async findOne(id: string): Promise<ReservationResponseDto> {
    const reservation = await this.reservationRepository.findOne({
      where: { id },
      relations: { table: true },
    });

    if (!reservation) {
      throw new EntityNotFoundException('Reservation', id);
    }

    return this.toResponse(reservation);
  }

  // HU-009: modifica una reserva existente sin generar conflictos.
  // Reasigna mesa solo si la actual ya no cumple capacidad o tiene solapamiento.
  async update(
    id: string,
    dto: UpdateReservationDto,
  ): Promise<ReservationResponseDto> {
    await this.reservationRepository.manager.transaction(async (manager) => {
      const repository = manager.getRepository(Reservation);
      const reservation = await repository.findOne({
        where: { id },
        lock: { mode: 'pessimistic_write' },
      });
      if (!reservation) throw new EntityNotFoundException('Reservation', id); // RN-052
      if (NOT_MODIFIABLE.includes(reservation.status)) {
        throw new BusinessRuleException(
          `Estado ${reservation.status}: no se modifica`,
          'RN-057',
        );
      }

      const schedule = {
        date: dto.date ?? reservation.date,
        time: dto.time ?? reservation.time.slice(0, 5), // BD: HH:MM:SS
        guests: dto.guests ?? reservation.guests,
      };
      const changed =
        schedule.date !== reservation.date ||
        schedule.time !== reservation.time.slice(0, 5) ||
        schedule.guests !== reservation.guests;

      if (changed) {
        if (!isFutureInRestaurantTz(schedule.date, schedule.time)) {
          throw new BadRequestException({
            message:
              'La fecha y hora deben ser futuras (zona horaria del restaurante)',
            rule: 'RN-053',
          });
        }
        const { tables } = await this.findAvailableTables(
          schedule,
          manager,
          reservation.id,
        );
        let keepsTable = tables.some((t) => t.id === reservation.tableId);

        if (keepsTable) {
          // Igual que lockFirstAvailableTable: bloquear la fila de la mesa y volver
          // a comprobar. Sin este paso, dos transacciones concurrentes podrían
          // asignar la misma mesa a dos reservas distintas (RN-045).
          await manager.getRepository(Table).findOne({
            // keepsTable es true solo si tables.some() encontró esa mesa → tableId no puede ser null
            where: { id: reservation.tableId! },
            lock: { mode: 'pessimistic_write' },
          });
          const again = await this.findAvailableTables(
            schedule,
            manager,
            reservation.id,
          );
          keepsTable = again.tables.some((t) => t.id === reservation.tableId);
        }

        if (!keepsTable) {
          const tableId = await this.lockFirstAvailableTable(
            schedule,
            manager,
            reservation.id,
          );
          if (!tableId) {
            throw new BusinessRuleException(
              'No hay mesas para ese cambio',
              'RN-058',
            );
          }
          reservation.tableId = tableId; // RN-056
        }
      }

      await repository.save(
        repository.merge(reservation, {
          customerName: dto.customerName ?? reservation.customerName,
          phone: dto.phone ?? reservation.phone,
          email: dto.email ?? reservation.email,
          ...schedule,
        }),
      );
    });
    return this.findOne(id);
  }

  // HU-010: cancela la reserva conservándola en el historial (RN-062). No se
  // toca la mesa: CANCELLED no está en BLOCKING_STATUSES, así que la
  // disponibilidad deja de contarla sola (RN-063).
  async cancel(id: string): Promise<ReservationResponseDto> {
    const reservation = await this.reservationRepository.findOne({
      where: { id },
      relations: { table: true },
    });

    if (!reservation) {
      throw new EntityNotFoundException('Reservation', id); // RN-059
    }

    // assertTransition respondería RN-057 (estado final); la HU pide RN-060
    if (reservation.status === ReservationStatus.CANCELLED) {
      throw new BusinessRuleException('La reserva ya está cancelada', 'RN-060');
    }

    // CHECKED_IN, COMPLETED o NO_SHOW → 409 con su regla (RN-061, RN-057)
    assertTransition(reservation.status, ReservationStatus.CANCELLED);

    reservation.status = ReservationStatus.CANCELLED;
    reservation.cancelledAt = new Date(); // RN-064
    const saved = await this.reservationRepository.save(reservation);

    return this.toResponse(saved);
  }

  // HU-011: confirma una reserva pendiente (PENDING → CONFIRMED).
  // Conserva la mesa asignada y registra cuándo se confirmó (RN-069).
  async confirm(id: string): Promise<ReservationResponseDto> {
    // 1. La reserva debe existir (RN-065). Se trae con su mesa para revisarla.
    const reservation = await this.reservationRepository.findOne({
      where: { id },
      relations: { table: true },
    });

    if (!reservation) {
      throw new EntityNotFoundException('Reservation', id);
    }

    // 2. Solo se confirma desde PENDING (RN-066, RN-067): si el estado actual
    // no permite pasar a CONFIRMED, lanza un 409.
    assertTransition(reservation.status, ReservationStatus.CONFIRMED);

    // 3. La reserva debe conservar una mesa operativa (RN-068). Si no tiene
    // mesa (por ejemplo, se eliminó) o está fuera de servicio, el personal
    // tiene que asignar o reasignar otra antes de confirmar (HU-009).
    if (
      !reservation.table ||
      reservation.table.status === TableStatus.OUT_OF_SERVICE
    ) {
      throw new BusinessRuleException(
        'La reserva no tiene una mesa operativa asignada: asigna o reasigna una mesa antes de confirmarla',
        'RN-068',
      );
    }

    // 4. Se cambia el estado y se guarda la fecha y hora de confirmación (RN-069).
    // La mesa no se toca: sigue asignada y la reserva sigue bloqueando el horario.
    reservation.status = ReservationStatus.CONFIRMED;
    reservation.confirmedAt = new Date();

    const saved = await this.reservationRepository.save(reservation);
    return this.toResponse(saved);
  }

  // HU-012: registra la llegada del cliente. En una sola transacción la
  // reserva pasa a CHECKED_IN y su mesa a OCCUPIED: o se aplican las dos
  // cosas o ninguna.
  async checkIn(id: string): Promise<ReservationResponseDto> {
    await this.reservationRepository.manager.transaction(async (manager) => {
      const reservations = manager.getRepository(Reservation);
      const tables = manager.getRepository(Table);

      // 1. La reserva debe existir (RN-070). Se bloquea su fila para que dos
      // check-in simultáneos de la misma reserva no pasen los dos.
      const reservation = await reservations.findOne({
        where: { id },
        lock: { mode: 'pessimistic_write' },
      });

      if (!reservation) {
        throw new EntityNotFoundException('Reservation', id);
      }

      // 2. Solo desde CONFIRMED (RN-071, RN-075): si el estado actual no
      // permite pasar a CHECKED_IN, lanza un 409.
      assertTransition(reservation.status, ReservationStatus.CHECKED_IN);

      // 3. Debe tener una mesa asignada (RN-072).
      if (!reservation.tableId) {
        throw new BusinessRuleException(
          'La reserva no tiene una mesa asignada: asigna una antes de registrar el check-in',
          'RN-072',
        );
      }

      // 4. Se carga y se bloquea la mesa dentro de la misma transacción.
      const table = await tables.findOne({
        where: { id: reservation.tableId },
        lock: { mode: 'pessimistic_write' },
      });

      if (!table) {
        throw new BusinessRuleException(
          'La mesa asignada a la reserva ya no existe: asigna otra antes de registrar el check-in',
          'RN-072',
        );
      }

      // 5. Si la mesa ya está ocupada por otra reserva o pedido, no se puede
      // ocupar de nuevo (RN-073).
      if (table.status === TableStatus.OCCUPIED) {
        throw new BusinessRuleException(
          'La mesa asignada ya está ocupada por otra reserva o pedido',
          'RN-073',
        );
      }

      // 6. Se aplican los dos cambios y se registra la llegada (RN-073, RN-074).
      reservation.status = ReservationStatus.CHECKED_IN;
      reservation.checkedInAt = new Date();
      table.status = TableStatus.OCCUPIED;

      await reservations.save(reservation);
      await tables.save(table);
    });

    // La transacción ya terminó: se devuelve la reserva con su mesa, en el
    // mismo formato que el GET.
    return this.findOne(id);
  }

  // Convierte la entidad en la respuesta pública: la hora en HH:MM y de la
  // mesa solo su resumen, o null si no tiene (RN-050).
  private toResponse(reservation: Reservation): ReservationResponseDto {
    const { table } = reservation;

    return {
      id: reservation.id,
      customerName: reservation.customerName,
      phone: reservation.phone,
      email: reservation.email,
      date: reservation.date,
      time: reservation.time.slice(0, 5), // PostgreSQL devuelve HH:MM:SS
      guests: reservation.guests,
      status: reservation.status,
      table: table
        ? {
            id: table.id,
            number: table.number,
            zone: table.zone,
            capacity: table.capacity,
          }
        : null,
      confirmedAt: reservation.confirmedAt ?? null,
      checkedInAt: reservation.checkedInAt ?? null,
      cancelledAt: reservation.cancelledAt ?? null,
      noShowAt: reservation.noShowAt ?? null,
      completedAt: reservation.completedAt ?? null,
      createdAt: reservation.createdAt,
      updatedAt: reservation.updatedAt,
    };
  }
}
