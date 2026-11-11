import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, tap } from 'rxjs';
import { apiBase } from './api';

export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

interface AuthResponse { email: string; role: Role; }

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly user = signal<AuthResponse | null>(null);
  readonly authenticated = computed(() => this.user() !== null);
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/login`, { email, password })
      .pipe(tap(u => this.user.set(u)));
  }

  register(name: string, email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/register`, { name, email, password })
      .pipe(tap(u => this.user.set(u)));
  }

  /** Se ejecuta al arrancar la app: pregunta al backend si la cookie HttpOnly es válida. */
  loadSession(): Observable<void> {
    // Limpia el token viejo que pudo quedar en navegadores de usuarios existentes
    try { localStorage.removeItem('skillbridge_token'); } catch { /* ignorar */ }

    return this.http.get<AuthResponse>(`${apiBase()}/auth/me`).pipe(
      tap(u => this.user.set(u)),
      catchError(() => { this.user.set(null); return of(null); }),
      map(() => void 0)
    );
  }

  isAuthenticated(): boolean { return this.authenticated(); }

  // Solo para mostrar/ocultar UI. La autorización real la hace SIEMPRE el backend.
  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return !!r && roles.includes(r);
  }
  /** Email del usuario autenticado (viene de /auth/me; ya no se decodifica de un JWT). */
  email(): string | null { return this.user()?.email ?? null; }

  logout(): void {
    this.http.post<void>(`${apiBase()}/auth/logout`, {})
      .pipe(finalize(() => {
        this.user.set(null);
        this.router.navigateByUrl('/');
      }))
      .subscribe({ error: () => { /* la sesión local se limpia en finalize */ } });
  }

  /** Para el interceptor: sesión expirada en el servidor. */
  clearSession(): void { this.user.set(null); }
}
