import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth-guard';
import { AuthService } from '../core/auth.service';

describe('authGuard', () => {
  const run: CanActivateFn = (...args) =>
    TestBed.runInInjectionContext(() => authGuard(...args));
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/ai' } as RouterStateSnapshot;

  function setup(authenticated: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: () => authenticated } },
      ],
    });
  }

  it('permite el acceso si hay sesión', () => {
    setup(true);
    expect(run(route, state)).toBe(true);
  });

  it('redirige a /login si no hay sesión', () => {
    setup(false);
    const result = run(route, state) as UrlTree;
    expect(result.toString()).toBe('/login');
  });
});
