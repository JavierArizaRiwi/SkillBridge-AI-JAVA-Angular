import { ApiProperty } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { IsEmail, IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class LoginDto {
  @ApiProperty({ example: 'carlos@example.com' })
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim().toLowerCase() : value,
  )
  @IsEmail()
  @MaxLength(255)
  email: string;

  // Sin la política de contraseñas: el login no debe dar pistas sobre ella
  @ApiProperty({ example: 'Password123' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  password: string;
}
