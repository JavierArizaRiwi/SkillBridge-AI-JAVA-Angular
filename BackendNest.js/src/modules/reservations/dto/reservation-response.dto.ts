import { ApiProperty } from '@nestjs/swagger';
import { ReservationStatus } from '../enums/index.js';
import { ReservationTableDto } from './reservation-table.dto.js';

/**
 * Lo que devuelve la API al consultar una reserva (HU-008).
 * Siempre muestra el estado actual (RN-049) y la mesa asignada,
 * o null si todavía no tiene una (RN-050).
 */
export class ReservationResponseDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ example: 'Carlos Pérez' })
  customerName: string;

  @ApiProperty({ example: '3001234567' })
  phone: string;

  @ApiProperty({ example: 'carlos@example.com' })
  email: string;

  @ApiProperty({ example: '2026-10-10', description: 'Fecha (YYYY-MM-DD)' })
  date: string;

  @ApiProperty({ example: '19:00', description: 'Hora (HH:MM)' })
  time: string;

  @ApiProperty({ example: 4 })
  guests: number;

  @ApiProperty({ enum: ReservationStatus, example: ReservationStatus.PENDING })
  status: ReservationStatus;

  @ApiProperty({ type: ReservationTableDto, nullable: true })
  table: ReservationTableDto | null;

  // Momento en que la reserva pasó a cada estado; null si nunca pasó.
  @ApiProperty({ type: Date, nullable: true })
  confirmedAt: Date | null;

  @ApiProperty({ type: Date, nullable: true })
  checkedInAt: Date | null;

  @ApiProperty({ type: Date, nullable: true })
  cancelledAt: Date | null;

  @ApiProperty({ type: Date, nullable: true })
  noShowAt: Date | null;

  @ApiProperty({ type: Date, nullable: true })
  completedAt: Date | null;

  @ApiProperty()
  createdAt: Date;

  @ApiProperty()
  updatedAt: Date;
}
