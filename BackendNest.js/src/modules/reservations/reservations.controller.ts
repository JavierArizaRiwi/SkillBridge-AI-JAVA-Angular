// Endpoints HTTP de las reservas (/api/v1/reservations). Cada historia añade
// aquí sus rutas y se las pasa al service.

import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiParam,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { UpdateReservationDto } from './dto/update-reservation.dto.js';
import { FilterReservationsDto } from './dto/filter-reservations.dto.js';
import { ReservationResponseDto } from './dto/reservation-response.dto.js';
import { ReservationsService } from './reservations.service.js';
import { Public } from '../auth/decorators/index.js';
import { AvailabilityQueryDto } from './dto/availability-query.dto.js';
import { AvailabilityResponseDto } from './dto/availability-response.dto.js';
import { CreateReservationDto } from './dto/create-reservation.dto.js';

@ApiTags('Reservations')
@ApiBearerAuth('bearer')
@Controller('reservations')
export class ReservationsController {
  constructor(private readonly reservationsService: ReservationsService) {}

  // GET /api/v1/reservations/availability?date=&time=&guests= (HU-006)
  @Public()
  @Get('availability')
  @ApiOperation({ summary: 'Consultar mesas disponibles' })
  @ApiResponse({ status: 200, type: AvailabilityResponseDto })
  @ApiResponse({
    status: 400,
    description: 'Parámetros inválidos o fecha pasada',
  })
  findAvailableTables(
    @Query() query: AvailabilityQueryDto,
  ): Promise<AvailabilityResponseDto> {
    return this.reservationsService.findAvailableTables(query);
  }

  // POST /api/v1/reservations (HU-007). Requiere token; qué roles pueden
  // reservar lo define la matriz de permisos de HU-017.
  @Post()
  @ApiOperation({
    summary: 'Registrar una reserva con asignación automática de mesa',
  })
  @ApiResponse({
    status: 201,
    description: 'Reserva creada en estado PENDING, con su mesa asignada',
    type: ReservationResponseDto,
  })
  @ApiResponse({ status: 400, description: 'Datos inválidos o fecha pasada' })
  @ApiResponse({ status: 401, description: 'Falta el token' })
  @ApiResponse({
    status: 409,
    description: 'No hay mesas disponibles para ese horario (RN-044)',
  })
  create(@Body() dto: CreateReservationDto): Promise<ReservationResponseDto> {
    return this.reservationsService.create(dto);
  }

  // GET /api/v1/reservations?date=&status=&tableId=&page=&limit= (HU-008)
  @Get()
  @ApiOperation({ summary: 'Listar reservas con filtros y paginación' })
  @ApiResponse({
    status: 200,
    description: 'Listado de reservas, incluidas canceladas y completadas',
    type: [ReservationResponseDto],
  })
  @ApiResponse({ status: 400, description: 'Filtros inválidos' })
  @ApiResponse({ status: 401, description: 'Falta el token' })
  findAll(
    @Query() filters: FilterReservationsDto,
  ): Promise<ReservationResponseDto[]> {
    return this.reservationsService.findAll(filters);
  }

  // GET /api/v1/reservations/{id} (HU-008)
  // Va al final del controller: así rutas fijas como /reservations/availability
  // (HU-006) no se confunden con un id.
  @Get(':id')
  @ApiOperation({ summary: 'Consultar una reserva con su mesa' })
  @ApiParam({ name: 'id', description: 'UUID de la reserva' })
  @ApiResponse({
    status: 200,
    description: 'Reserva encontrada',
    type: ReservationResponseDto,
  })
  @ApiResponse({ status: 400, description: 'El id no es un UUID' })
  @ApiResponse({ status: 401, description: 'Falta el token' })
  @ApiResponse({ status: 404, description: 'La reserva no existe' })
  findOne(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ReservationResponseDto> {
    return this.reservationsService.findOne(id);
  }

  // PATCH /api/v1/reservations/{id} (HU-009)
  @Patch(':id')
  @ApiOperation({ summary: 'Modificar una reserva existente' })
  @ApiParam({ name: 'id', description: 'UUID de la reserva' })
  @ApiResponse({
    status: 200,
    description: 'Reserva modificada exitosamente',
    type: ReservationResponseDto,
  })
  @ApiResponse({
    status: 400,
    description: 'Datos inválidos o fecha pasada (RN-053)',
  })
  @ApiResponse({ status: 401, description: 'Falta el token' })
  @ApiResponse({ status: 404, description: 'La reserva no existe (RN-052)' })
  @ApiResponse({
    status: 409,
    description:
      'Estado no modificable (RN-057) o sin disponibilidad de mesas (RN-058)',
  })
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateReservationDto,
  ): Promise<ReservationResponseDto> {
    return this.reservationsService.update(id, dto);
  }

  // PATCH /api/v1/reservations/{id}/cancel (HU-010)
  @Patch(':id/cancel')
  @ApiOperation({ summary: 'Cancelar una reserva' })
  @ApiParam({ name: 'id', description: 'UUID de la reserva' })
  @ApiResponse({
    status: 200,
    description: 'Reserva cancelada; se conserva en el historial',
    type: ReservationResponseDto,
  })
  @ApiResponse({ status: 400, description: 'El id no es un UUID' })
  @ApiResponse({ status: 401, description: 'Falta el token' })
  @ApiResponse({ status: 404, description: 'La reserva no existe' })
  @ApiResponse({
    status: 409,
    description:
      'El estado no permite cancelar (ya cancelada RN-060, con check-in RN-061, estado final RN-057)',
  })
  cancel(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ReservationResponseDto> {
    return this.reservationsService.cancel(id);
  }

  // PATCH /api/v1/reservations/{id}/confirm (HU-011)
  @Patch(':id/confirm')
  @ApiOperation({ summary: 'Confirmar una reserva pendiente' })
  @ApiParam({ name: 'id', description: 'UUID de la reserva' })
  @ApiResponse({
    status: 200,
    description: 'Reserva confirmada (PENDING → CONFIRMED)',
    type: ReservationResponseDto,
  })
  @ApiResponse({ status: 400, description: 'El id no es un UUID válido' })
  @ApiResponse({ status: 401, description: 'Falta el token' })
  @ApiResponse({ status: 404, description: 'Reserva no encontrada (RN-065)' })
  @ApiResponse({
    status: 409,
    description:
      'La reserva no está PENDING (RN-066, RN-067) o su mesa está fuera de servicio (RN-068)',
  })
  confirm(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ReservationResponseDto> {
    return this.reservationsService.confirm(id);
  }
  // PATCH /api/v1/reservations/{id}/check-in (HU-012)
  @Patch(':id/check-in')
  @ApiOperation({ summary: 'Registrar el check-in de una reserva' })
  @ApiParam({ name: 'id', description: 'UUID de la reserva' })
  @ApiResponse({
    status: 200,
    description:
      'Check-in registrado: la reserva pasa a CHECKED_IN y su mesa a OCCUPIED',
    type: ReservationResponseDto,
  })
  @ApiResponse({ status: 400, description: 'El id no es un UUID válido' })
  @ApiResponse({ status: 401, description: 'Falta el token' })
  @ApiResponse({ status: 404, description: 'La reserva no existe (RN-070)' })
  @ApiResponse({
    status: 409,
    description:
      'La reserva no está CONFIRMED (RN-071, RN-075), no tiene mesa asignada (RN-072) o su mesa ya está ocupada',
  })
  checkIn(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ReservationResponseDto> {
    return this.reservationsService.checkIn(id);
  }
}
