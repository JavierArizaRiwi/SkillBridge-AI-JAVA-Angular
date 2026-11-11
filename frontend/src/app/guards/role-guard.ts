import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from '../core/auth.service';

// Uso: canActivate: [roleGuard('PROVIDER', 'ADMIN')]
export const roleGuard = (...roles: Role[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  return auth.hasRole(...roles) ? true : router.createUrlTree(['/']);
};
