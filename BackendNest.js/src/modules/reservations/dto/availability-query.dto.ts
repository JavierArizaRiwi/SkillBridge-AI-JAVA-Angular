import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsInt, IsISO8601, Matches, Min } from 'class-validator';
import { IsFutureDateTime } from '../../../common/validators/is-future-date-time.validator.js';

export class AvailabilityQueryDto {
  // 'YYYY-MM-DD'; rechaza fechas imposibles como 2026-02-31
  @ApiProperty({
    example: '2026-10-20',
    description: 'Fecha de la reserva (YYYY-MM-DD)',
  })
  @IsISO8601({ strict: true })
  @Matches(/^\d{4}-\d{2}-\d{2}$/, {
    message: 'date debe tener formato YYYY-MM-DD',
  })
  date: string;

  // 'HH:mm'; además combina con `date` y exige que sea futuro
  @ApiProperty({
    example: '20:00',
    description:
      'Hora de la reserva en formato 24 h (HH:mm). Junto con la fecha debe ser futura en la zona horaria del restaurante',
  })
  @Matches(/^([01]\d|2[0-3]):[0-5]\d$/, {
    message: 'time debe tener formato HH:mm',
  })
  @IsFutureDateTime('date')
  time: string;

  // En query string todo llega como texto: @Type lo convierte a número
  @ApiProperty({ example: 4, minimum: 1, description: 'Número de personas' })
  @Type(() => Number)
  @IsInt()
  @Min(1)
  guests: number;
}
