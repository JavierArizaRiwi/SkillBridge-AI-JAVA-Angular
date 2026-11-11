import { SetMetadata } from '@nestjs/common';
import type { UserRole } from '../../users/enums/index.js';

export const ROLES_KEY = 'roles';

/**
 * Restringe un controlador o una ruta a ciertos roles (RN-101).
 * Sin @Roles basta con estar autenticado; con @Roles, un usuario cuyo rol no
 * esté en la lista recibe 403 (RN-102).
 *
 * Uso: @Roles(UserRole.ADMIN, UserRole.WAITER)
 */
export const Roles = (...roles: UserRole[]) => SetMetadata(ROLES_KEY, roles);
