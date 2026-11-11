// Este archivo es como un "formulario" que dice qué datos necesitamos
// para crear una mesa nueva, y qué reglas debe cumplir cada dato.

import { ApiProperty } from '@nestjs/swagger';
import { IsEnum, IsInt, IsPositive } from 'class-validator';
import { TableZone } from '../enums/index.js';

export class CreateTableDto {
  // @ApiProperty: le pone una etiqueta bonita en Swagger para que
  // cualquiera vea qué es este campo, sin adivinar.
  @ApiProperty({
    example: 5,
    description: 'Número de mesa, entero positivo y único',
  })
  // @IsInt: dice "esto tiene que ser un número entero, no puede ser 4.5"
  @IsInt()
  // @IsPositive: dice "el número tiene que ser mayor que 0, no puede ser -3 ni 0"
  @IsPositive()
  number: number;

  @ApiProperty({
    example: 4,
    description: 'Capacidad de la mesa, mayor que cero (RN-017)',
  })
  @IsInt()
  @IsPositive() // igual que arriba: la capacidad no puede ser 0 ni negativa
  capacity: number;

  @ApiProperty({ enum: TableZone, example: TableZone.INTERIOR })
  // @IsEnum: dice "esto solo puede ser una de las opciones de la lista TableZone"
  // (INTERIOR, TERRACE, BAR o VIP). Si mandas "PLAYA", lo rechaza.
  @IsEnum(TableZone)
  zone: TableZone;
}
