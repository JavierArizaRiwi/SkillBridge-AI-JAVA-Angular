import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';

export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  const token = localStorage.getItem('skillbridge_token');
  
  if (token) {
    return true;
  }
  
  return router.createUrlTree(['/login']);
};
