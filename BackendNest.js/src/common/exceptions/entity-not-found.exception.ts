import { NotFoundException } from '@nestjs/common';

/**
 * 404 para cualquier recurso de dominio inexistente.
 * Uso: throw new EntityNotFoundException('Mesa', id);
 */
export class EntityNotFoundException extends NotFoundException {
  constructor(entity: string, id: string | number) {
    super(`${entity} con id ${id} no existe`);
  }
}
