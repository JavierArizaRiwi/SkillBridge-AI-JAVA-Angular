import { applyDecorators } from '@nestjs/common';
import { IsString, Matches, MaxLength, MinLength } from 'class-validator';

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 128;

/**
 * Política de contraseñas del proyecto (D-002): 8–128 caracteres con al menos
 * una letra y un número (RN-083, RN-105). La comparten el registro (HU-014),
 * el alta de empleados (HU-016) y el restablecimiento (HU-018).
 */
export function MeetsPasswordPolicy() {
  return applyDecorators(
    IsString(),
    MinLength(PASSWORD_MIN_LENGTH, {
      message: `La contraseña debe tener al menos ${PASSWORD_MIN_LENGTH} caracteres`,
    }),
    MaxLength(PASSWORD_MAX_LENGTH, {
      message: `La contraseña no puede superar los ${PASSWORD_MAX_LENGTH} caracteres`,
    }),
    Matches(/^(?=.*\p{L})(?=.*\p{N}).*$/u, {
      message: 'La contraseña debe contener al menos una letra y un número',
    }),
  );
}
