import { OmitType, PartialType } from '@nestjs/swagger';
import { CreateEmployeeDto } from './create-employee.dto.js';

// Todo opcional y sin email ni contraseña. El estado se cambia en
// PATCH /employees/{id}/status y el rol con HU-017; como el ValidationPipe
// usa forbidNonWhitelisted, enviar cualquiera de ellos aquí responde 400.
export class UpdateEmployeeDto extends PartialType(
  OmitType(CreateEmployeeDto, ['email', 'password'] as const),
) {}
