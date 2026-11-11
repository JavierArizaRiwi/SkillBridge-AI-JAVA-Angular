import { ReservationTableDto } from './reservation-table.dto.js';
import { ApiProperty } from '@nestjs/swagger';

export class AvailabilityResponseDto {
  @ApiProperty({ example: true })
  available: boolean;

  @ApiProperty({ type: [ReservationTableDto] })
  tables: ReservationTableDto[];
}
