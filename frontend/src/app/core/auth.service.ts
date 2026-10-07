import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { apiBase } from './api';

interface AuthResponse { token: string; tokenType: string; }
export type Role = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly key = 'skillbridge_token';
  readonly authenticated = signal(!!localStorage.getItem(this.key));
  readonly role = signal<Role | null>(this.decodeRole(localStorage.getItem(this.key)));

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/login`, { email, password })
      .pipe(tap(r => this.save(r.token)));
  }

  register(name: string, email: string, password: string) {
    return this.http.post<AuthResponse>(`${apiBase()}/auth/register`, { name, email, password })
      .pipe(tap(r => this.save(r.token)));
  }

  token(): string | null { return localStorage.getItem(this.key); }
  isAuthenticated(): boolean { return this.authenticated(); }
  hasRole(...roles: Role[]): boolean { const r = this.role(); return !!r && roles.includes(r); }

  logout(): void {
    localStorage.removeItem(this.key);
    this.authenticated.set(false);
    this.role.set(null);
    this.router.navigateByUrl('/');
  }

  private save(token: string): void {
    localStorage.setItem(this.key, token);
    this.authenticated.set(true);
    this.role.set(this.decodeRole(token));
  }

  // Solo para mostrar/ocultar UI. La autorización real la hace SIEMPRE el backend.
  private decodeRole(token: string | null): Role | null {
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return (payload.role as Role) ?? null;
    } catch { return null; }
  }
}
