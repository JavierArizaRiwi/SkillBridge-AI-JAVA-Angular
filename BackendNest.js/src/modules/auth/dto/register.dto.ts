import { ApiProperty } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsEmail,
  IsNotEmpty,
  IsString,
  Matches,
  MaxLength,
} from 'class-validator';
import {
  Match,
  MeetsPasswordPolicy,
} from '../../../common/validators/index.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

export class RegisterDto {
  @ApiProperty({ example: 'Carlos', maxLength: 100 })
  @Transform(trim)
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  firstName: string;

  @ApiProperty({ example: 'Pérez', maxLength: 100 })
  @Transform(trim)
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  lastName: string;

  // Se guarda en minúsculas: Carlos@Example.com y carlos@example.com son la misma cuenta
  @ApiProperty({ example: 'carlos@example.com', maxLength: 255 })
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim().toLowerCase() : value,
  )
  @IsEmail()
  @MaxLength(255)
  email: string;

  @ApiProperty({
    example: '+57 300 123 4567',
    description:
      'Entre 7 y 20 caracteres: dígitos, espacios, guiones y un + inicial',
  })
  @Transform(trim)
  @IsString()
  @Matches(/^\+?[0-9][0-9 -]{5,18}[0-9]$/, {
    message: 'El teléfono no tiene un formato válido',
  })
  phone: string;

  @ApiProperty({
    example: 'Password123',
    minLength: 8,
    description: 'Al menos 8 caracteres, con una letra y un número (RN-083)',
  })
  @MeetsPasswordPolicy()
  password: string;

  @ApiProperty({ example: 'Password123' })
  @IsString()
  @Match('password', {
    message: 'La confirmación no coincide con la contraseña',
  })
  passwordConfirmation: string;
}
