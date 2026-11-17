import { type CanActivate, type ExecutionContext, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request } from 'express';
import { DomainErrors } from '../common/domain-error.js';
import type { AuthenticatedUser, UserRole } from './auth.types.js';
import { ROLES_KEY } from './roles.decorator.js';

interface AuthRequest extends Request {
  user?: AuthenticatedUser;
}

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const required = this.reflector.getAllAndOverride<UserRole[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    if (!required || required.length === 0) {
      return true;
    }

    const request = context.switchToHttp().getRequest<AuthRequest>();
    const user = request.user;
    if (!user) {
      throw DomainErrors.unauthenticated();
    }
    if (!required.includes(user.role)) {
      throw DomainErrors.forbidden('Rol sin permiso para esta operación');
    }
    return true;
  }
}
