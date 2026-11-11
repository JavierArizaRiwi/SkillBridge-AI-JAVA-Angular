import { ApiProperty } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsEmail,
  IsEnum,
  IsNotEmpty,
  IsString,
  Matches,
  MaxLength,
} from 'class-validator';
import { MeetsPasswordPolicy } from '../../../common/validators/index.js';
import { EmployeePosition } from '../enums/index.js';

const trim = ({ value }: { value: unknown }) =>
  typeof value === 'string' ? value.trim() : value;

// Datos para dar de alta un empleado (HU-016). Mismas validaciones que el
// registro de clientes (RN-082, RN-083) más el cargo obligatorio (RN-096).
export class CreateEmployeeDto {
  @ApiProperty({ example: 'Ana', maxLength: 100 })
  @Transform(trim)
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  firstName: string;

  @ApiProperty({ example: 'López', maxLength: 100 })
  @Transform(trim)
  @IsString()
  @IsNotEmpty()
  @MaxLength(100)
  lastName: string;

  // Se guarda en minúsculas, igual que en el registro
  @ApiProperty({ example: 'ana.lopez@zerobug.dev', maxLength: 255 })
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

  @ApiProperty({ enum: EmployeePosition, example: EmployeePosition.WAITER })
  @IsEnum(EmployeePosition)
  position: EmployeePosition;

  // Contraseña inicial que el administrador le entrega al empleado
  @ApiProperty({
    example: 'Password123',
    minLength: 8,
    description: 'Al menos 8 caracteres, con una letra y un número (RN-083)',
  })
  @MeetsPasswordPolicy()
  password: string;
}
