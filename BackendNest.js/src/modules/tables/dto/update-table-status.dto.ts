// Este archivo es un formulario chiquito y especial: solo sirve
// para cambiar el "estado" de una mesa (si está libre, ocupada, etc).

import { ApiProperty } from '@nestjs/swagger';
import { IsEnum } from 'class-validator';
import { TableStatus } from '../enums/index.js';

export class UpdateTableStatusDto {
  @ApiProperty({ enum: TableStatus, example: TableStatus.OCCUPIED })
  // Solo se permiten los 3 estados que existen: AVAILABLE, OCCUPIED, OUT_OF_SERVICE.
  // Si alguien manda "BAILANDO", el mensaje de error le dice cuáles sí valen.
  @IsEnum(TableStatus, {
    message: `status debe ser uno de: ${Object.values(TableStatus).join(', ')}`,
  })
  status: TableStatus;
}
