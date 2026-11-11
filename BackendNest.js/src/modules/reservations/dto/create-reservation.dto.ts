// Este archivo es el "formulario" de POST /api/v1/reservations: dice qué datos
// necesitamos para registrar una reserva y qué reglas debe cumplir cada uno.

import { ApiProperty } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsEmail,
  IsInt,
  IsISO8601,
  IsPositive,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';
import { IsFutureDateTime } from '../../../common/validators/index.js';

// Teléfono flexible: entre 7 y 15 dígitos, con "+" opcional al inicio y
// permitiendo espacios, guiones, puntos y paréntesis como separadores.
const PHONE_PATTERN = /^(?=(?:\D*\d){7,15}\D*$)\+?[\d\s().-]+$/;

// Fecha con formato YYYY-MM-DD (por ejemplo 2026-12-20).
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

// Hora en formato 24 h, HH:mm (por ejemplo 19:20).
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

// Quita los espacios del inicio y del final de un texto antes de validarlo.
// Así "   " (solo espacios) no se cuela como un nombre válido.
const trim = ({ value }: { value: unknown }): unknown =>
  typeof value === 'string' ? value.trim() : value;

export class CreateReservationDto {
  @ApiProperty({ example: 'Carlos Pérez', minLength: 2, maxLength: 100 })
  @Transform(trim)
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  customerName: string;

  @ApiProperty({
    example: '3001234567',
    description:
      'Teléfono de contacto: 7 a 15 dígitos (admite +, espacios, guiones y paréntesis)',
  })
  @Transform(trim)
  @IsString()
  @Matches(PHONE_PATTERN, {
    message: 'phone debe tener entre 7 y 15 dígitos',
  })
  phone: string;

  @ApiProperty({ example: 'carlos@example.com' })
  @Transform(trim)
  @IsEmail()
  email: string;

  @ApiProperty({
    example: '2026-12-20',
    description: 'Fecha de la reserva (YYYY-MM-DD)',
  })
  @Matches(DATE_PATTERN, {
    message: 'date debe tener el formato YYYY-MM-DD',
  })
  @IsISO8601({ strict: true }, { message: 'date debe ser una fecha real' })
  date: string;

  @ApiProperty({
    example: '19:00',
    description:
      'Hora de la reserva en formato 24 h (HH:mm). La fecha y la hora juntas deben ser futuras en la zona horaria del restaurante (RN-042)',
  })
  @Matches(TIME_PATTERN, {
    message: 'time debe tener el formato HH:mm (24 horas)',
  })
  @IsFutureDateTime('date')
  time: string;

  @ApiProperty({
    example: 4,
    minimum: 1,
    description: 'Número de personas, mayor que cero (RN-043)',
  })
  @IsInt()
  @IsPositive()
  guests: number;
}
