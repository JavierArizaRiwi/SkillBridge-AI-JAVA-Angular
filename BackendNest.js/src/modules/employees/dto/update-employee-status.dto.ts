import { ApiProperty } from '@nestjs/swagger';
import { IsEnum } from 'class-validator';
import { UserStatus } from '../../users/enums/index.js';

// Activar o desactivar la cuenta de un empleado. INACTIVE no puede iniciar
// sesión (RN-098).
export class UpdateEmployeeStatusDto {
  @ApiProperty({ enum: UserStatus, example: UserStatus.INACTIVE })
  @IsEnum(UserStatus)
  status: UserStatus;
}
