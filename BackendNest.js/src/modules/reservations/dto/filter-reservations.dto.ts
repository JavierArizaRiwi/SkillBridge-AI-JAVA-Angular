import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsDateString,
  IsEnum,
  IsInt,
  IsOptional,
  IsUUID,
  Max,
  Min,
} from 'class-validator';
import { ReservationStatus } from '../enums/index.js';

/**
 * Filtros opcionales del listado GET /reservations (HU-008).
 * Todo llega como texto en la URL (?page=2), por eso los campos numéricos
 * llevan @Type(() => Number) para convertirse antes de validarse.
 */
export class FilterReservationsDto {
  @ApiPropertyOptional({
    example: '2026-10-10',
    description: 'Fecha de la reserva (YYYY-MM-DD)',
  })
  @IsOptional()
  @IsDateString({}, { message: 'La fecha debe tener formato YYYY-MM-DD' })
  date?: string;

  @ApiPropertyOptional({
    enum: ReservationStatus,
    description: 'Estado; incluye CANCELLED y COMPLETED (RN-051)',
  })
  @IsOptional()
  @IsEnum(ReservationStatus)
  status?: ReservationStatus;

  @ApiPropertyOptional({ format: 'uuid', description: 'Mesa asignada' })
  @IsOptional()
  @IsUUID()
  tableId?: string;

  @ApiPropertyOptional({ example: 1, default: 1 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page: number = 1;

  @ApiPropertyOptional({ example: 10, default: 10, maximum: 100 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  limit: number = 10;
}
