import { ApiPropertyOptional, OmitType, PartialType } from '@nestjs/swagger';
import { CreateReservationDto } from './create-reservation.dto.js';
import { IsOptional, Matches } from 'class-validator';

export class UpdateReservationDto extends PartialType(
  OmitType(CreateReservationDto, ['time'] as const),
) {
  @ApiPropertyOptional({ example: '20:30' })
  @IsOptional()
  @Matches(/^([01]\d|2[0-3]):[0-5]\d$/, {
    message: 'time debe tener el formato HH:mm (24 horas)',
  })
  time?: string;
}
