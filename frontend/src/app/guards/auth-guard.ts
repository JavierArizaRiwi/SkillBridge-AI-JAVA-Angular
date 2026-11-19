// auth.guard.ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../core/auth.service'; // Reemplaza por tu servicio real

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) { 
    return true; // Permite el acceso a la vista
  }

  // Si no está autenticado, redirige al login
  return router.createUrlTree(['/login']); 
};
