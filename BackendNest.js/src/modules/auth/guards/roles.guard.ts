import {
  ForbiddenException,
  Injectable,
  type CanActivate,
  type ExecutionContext,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request } from 'express';
import { ROLES_KEY } from '../decorators/index.js';
import type { UserRole } from '../../users/enums/index.js';
import type { AuthenticatedUser } from '../interfaces/index.js';

// Guard global que se ejecuta después de JwtAuthGuard (ver AuthModule): ya hay
// un usuario autenticado y aquí solo se comprueba su rol (RN-100 … RN-102).
@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const required = this.reflector.getAllAndOverride<UserRole[] | undefined>(
      ROLES_KEY,
      [context.getHandler(), context.getClass()],
    );

    // Sin @Roles basta con estar autenticado (o la ruta es @Public)
    if (!required || required.length === 0) return true;

    const { user } = context
      .switchToHttp()
      .getRequest<Request & { user?: AuthenticatedUser }>();

    if (!user || !required.includes(user.role)) {
      throw new ForbiddenException(
        'No tienes permisos para realizar esta operación',
      );
    }
    return true;
  }
}
