// Este archivo describe los "filtros" que alguien puede usar para buscar
// mesas, como cuando buscas zapatos en una tienda online y filtras
// por talla, color, precio... aquí filtramos por estado, zona y capacidad.

import { ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsEnum, IsInt, IsOptional, IsPositive } from 'class-validator';
import { TableStatus, TableZone } from '../enums/index.js';

export class FilterTablesDto {
  @ApiPropertyOptional({ enum: TableStatus })
  // @IsOptional: dice "este filtro no es obligatorio, puedes no usarlo"
  @IsOptional()
  @IsEnum(TableStatus)
  status?: TableStatus;

  @ApiPropertyOptional({ enum: TableZone })
  @IsOptional()
  @IsEnum(TableZone)
  zone?: TableZone;

  @ApiPropertyOptional({
    example: 4,
    description: 'Capacidad mínima a filtrar',
  })
  @IsOptional()
  // Cuando alguien busca por internet (?capacity=4), ese "4" en realidad
  // llega como texto ("4"), no como número. @Type(() => Number) lo convierte
  // en número de verdad antes de revisarlo, como traducir de un idioma a otro.
  @Type(() => Number)
  @IsInt()
  @IsPositive()
  capacity?: number;
}
