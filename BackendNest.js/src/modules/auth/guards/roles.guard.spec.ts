import { describe, it, expect, beforeEach } from 'vitest';
import { ForbiddenException, type ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { UserRole } from '../../users/enums/index.js';
import { Roles } from '../decorators/index.js';
import type { AuthenticatedUser } from '../interfaces/index.js';
import { RolesGuard } from './roles.guard.js';

// Controladores de prueba con los distintos usos de @Roles
class OpenController {
  anyone() {}
}

@Roles(UserRole.ADMIN)
class AdminController {
  adminOnly() {}

  @Roles(UserRole.ADMIN, UserRole.WAITER)
  staff() {}
}

const contextFor = (
  controller: new () => object,
  method: string,
  user?: AuthenticatedUser,
) =>
  ({
    getClass: () => controller,
    getHandler: () =>
      (controller.prototype as Record<string, () => void>)[method],
    switchToHttp: () => ({ getRequest: () => ({ user }) }),
  }) as unknown as ExecutionContext;

const userWith = (role: UserRole): AuthenticatedUser => ({
  id: 'u-1',
  email: 'a@example.com',
  role,
});

describe('RolesGuard (RN-100 … RN-102)', () => {
  let guard: RolesGuard;

  beforeEach(() => {
    guard = new RolesGuard(new Reflector());
  });

  it('sin @Roles deja pasar a cualquier usuario autenticado', () => {
    const ctx = contextFor(
      OpenController,
      'anyone',
      userWith(UserRole.CUSTOMER),
    );
    expect(guard.canActivate(ctx)).toBe(true);
  });

  it('sin @Roles deja pasar también una ruta pública (sin usuario)', () => {
    expect(guard.canActivate(contextFor(OpenController, 'anyone'))).toBe(true);
  });

  it('con @Roles deja pasar a un rol incluido', () => {
    const ctx = contextFor(
      AdminController,
      'adminOnly',
      userWith(UserRole.ADMIN),
    );
    expect(guard.canActivate(ctx)).toBe(true);
  });

  it('con @Roles responde 403 a un rol no incluido (RN-102)', () => {
    const ctx = contextFor(
      AdminController,
      'adminOnly',
      userWith(UserRole.CUSTOMER),
    );
    expect(() => guard.canActivate(ctx)).toThrow(ForbiddenException);
  });

  it('@Roles del método tiene prioridad sobre el de la clase', () => {
    const ctx = contextFor(AdminController, 'staff', userWith(UserRole.WAITER));
    expect(guard.canActivate(ctx)).toBe(true);
  });

  it('con @Roles responde 403 si no hay usuario autenticado', () => {
    expect(() =>
      guard.canActivate(contextFor(AdminController, 'adminOnly')),
    ).toThrow(ForbiddenException);
  });
});
