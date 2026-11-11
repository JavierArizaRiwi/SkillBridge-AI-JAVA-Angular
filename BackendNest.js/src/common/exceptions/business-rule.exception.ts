import { ConflictException } from '@nestjs/common';

/**
 * 409 para una regla de negocio incumplida (número de mesa duplicado,
 * transición de estado no permitida, sin disponibilidad, etc.).
 * `rule` referencia la RN del documento de la HU para trazabilidad.
 *
 * Uso: throw new BusinessRuleException('El número de mesa ya existe', 'RN-016');
 */
export class BusinessRuleException extends ConflictException {
  constructor(message: string, rule?: string) {
    super({ message, error: 'Conflict', rule });
  }
}
