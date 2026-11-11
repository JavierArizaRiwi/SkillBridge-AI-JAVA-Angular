import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, MaxLength } from 'class-validator';
import {
  Match,
  MeetsPasswordPolicy,
} from '../../../common/validators/index.js';

export class ResetPasswordDto {
  @ApiProperty({
    description: 'Token recibido en el enlace de recuperación',
    example: 'k3Jm…',
  })
  @IsString()
  @IsNotEmpty()
  @MaxLength(200)
  token: string;

  @ApiProperty({
    example: 'NuevaClave123',
    minLength: 8,
    description:
      'Misma política que el registro: 8+ caracteres, letra y número (RN-105)',
  })
  @MeetsPasswordPolicy()
  password: string;

  @ApiProperty({ example: 'NuevaClave123' })
  @IsString()
  @Match('password', {
    message: 'La confirmación no coincide con la contraseña',
  })
  passwordConfirmation: string;
}
