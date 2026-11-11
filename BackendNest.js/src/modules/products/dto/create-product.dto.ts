import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsNumber,
  IsOptional,
  IsPositive,
  IsString,
  IsUUID,
  MaxLength,
  MinLength,
} from 'class-validator';

export class CreateProductDto {
  @ApiProperty({ example: 'Hamburguesa Clásica', minLength: 2, maxLength: 100 })
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  name: string;

  @ApiPropertyOptional({
    example: 'Carne 150 g, queso cheddar, pan brioche',
    maxLength: 500,
  })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  description?: string;

  @ApiProperty({
    example: 25000,
    description: 'Mayor que cero, máximo 2 decimales (RN-026)',
  })
  @IsNumber({ maxDecimalPlaces: 2 })
  @IsPositive()
  price: number;

  @ApiProperty({
    example: '4f3c2a1e-9b8d-4c7a-a6e5-1d2c3b4a5f60',
    format: 'uuid',
    description: 'Categoría existente a la que pertenece (RN-025)',
  })
  @IsUUID()
  categoryId: string;
}
