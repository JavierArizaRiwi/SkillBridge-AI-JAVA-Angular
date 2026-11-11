import { ApiProperty } from '@nestjs/swagger';
import { TableZone } from '../../tables/enums/index.js';

/**
 * Resumen de la mesa asignada a una reserva (RN-050).
 * Se usa dentro de la respuesta de una reserva: solo muestra lo que le
 * sirve a quien consulta, no la entidad Table completa.
 */
export class ReservationTableDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ example: 5 })
  number: number;

  @ApiProperty({ enum: TableZone, example: TableZone.INTERIOR })
  zone: TableZone;

  @ApiProperty({ example: 4 })
  capacity: number;
}
