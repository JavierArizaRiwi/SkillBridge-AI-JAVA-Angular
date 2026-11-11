import { PartialType, PickType } from '@nestjs/swagger';
import { RegisterDto } from '../../auth/dto/index.js';

// Solo nombre, apellido y teléfono, con las mismas validaciones del registro.
// Como el ValidationPipe usa forbidNonWhitelisted, enviar email, role, status
// o password responde 400 (RN-110).
export class UpdateProfileDto extends PartialType(
  PickType(RegisterDto, ['firstName', 'lastName', 'phone'] as const),
) {}
