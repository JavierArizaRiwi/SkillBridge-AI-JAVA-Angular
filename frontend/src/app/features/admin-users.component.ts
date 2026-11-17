import { Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService, Role } from '../core/auth.service';
import { AdminUser, AdminUserService, UserStatus } from '../core/admin-user.service';

@Component({
  standalone: true,
  template: `
    <section class="min-h-screen bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto max-w-5xl">

        <!-- Encabezado -->
        <header class="enter-rise flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">Usuarios</h1>
            <p class="mt-4 text-base text-[#787774] tabular-nums">{{ totalElements }} usuarios en total</p>
          </div>

          <div class="flex flex-wrap gap-2">
            <select class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]"
                    (change)="changeSort($any($event.target).value)" aria-label="Ordenar por">
              @for (o of sortOptions; track o.value) {
                <option [value]="o.value" [selected]="o.value === sort">{{ o.label }}</option>
              }
            </select>
            <select class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]"
                    (change)="changeSize($any($event.target).value)" aria-label="Usuarios por página">
              @for (s of pageSizes; track s) {
                <option [value]="s" [selected]="s === size">{{ s }} por página</option>
              }
            </select>
          </div>
        </header>

        @if (error) {
          <div role="alert" class="enter-rise mt-8 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">{{ error }}</div>
        }
        @if (notice) {
          <div role="status" class="enter-rise mt-8 rounded-lg bg-[#EDF3EC] px-4 py-3 text-sm text-[#346538]">{{ notice }}</div>
        }

        <!-- Tabla: sin contenedor, solo líneas de fila -->
        <div class="enter-rise mt-12 overflow-x-auto" style="animation-delay: 60ms">
          <table class="min-w-full text-left text-sm">
            <thead>
              <tr class="border-b border-[#EAEAEA] text-[#787774]">
                <th class="py-3 pr-4 font-medium">Nombre</th>
                <th class="px-4 py-3 font-medium">Correo</th>
                <th class="px-4 py-3 font-medium">Rol</th>
                <th class="px-4 py-3 font-medium">Estado</th>
                <th class="py-3 pl-4 text-right font-medium">Acción</th>
              </tr>
            </thead>
            <tbody>
              @for (u of users; track u.id) {
                <tr class="border-b border-[#EAEAEA] transition-colors duration-150 hover:bg-[#F7F6F3]">
                  <td class="py-4 pr-4 font-medium text-[#111111]">
                    {{ u.name }}
                    @if (isMe(u)) {
                      <span class="ml-2 rounded-md bg-[#111111] px-1.5 py-0.5 text-xs font-medium text-white">Tú</span>
                    }
                  </td>
                  <td class="px-4 py-4 text-[#787774]">{{ u.email }}</td>
                  <td class="px-4 py-4">
                    <select class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-2.5 py-1.5 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111] disabled:cursor-not-allowed disabled:opacity-40"
                            [disabled]="isMe(u)" (change)="setRole(u, $any($event.target))" [attr.aria-label]="'Rol de ' + u.name">
                      @for (r of roles; track r) {
                        <option [value]="r" [selected]="r === u.role">{{ r }}</option>
                      }
                    </select>
                  </td>
                  <td class="px-4 py-4">
                    @if (u.status === 'ACTIVE') {
                      <span class="rounded-md bg-[#EDF3EC] px-2 py-0.5 text-xs font-medium text-[#346538]">Activa</span>
                    } @else {
                      <span class="rounded-md bg-[#FDEBEC] px-2 py-0.5 text-xs font-medium text-[#9F2F2D]">Suspendida</span>
                    }
                  </td>
                  <td class="py-4 pl-4 text-right">
                    <button type="button" [disabled]="isMe(u)" (click)="toggleStatus(u)"
                            [class]="u.status === 'ACTIVE' ? 'text-[#9F2F2D] hover:bg-[#FDEBEC]' : 'text-[#346538] hover:bg-[#EDF3EC]'"
                            class="cursor-pointer rounded-md px-3 py-1.5 text-sm font-medium transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100">
                      {{ u.status === 'ACTIVE' ? 'Suspender' : 'Reactivar' }}
                    </button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="py-24 text-center text-sm text-[#787774]">
                    {{ loading ? 'Cargando usuarios…' : 'No hay usuarios para mostrar.' }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Paginación -->
        <div class="mt-6 flex items-center justify-between text-sm text-[#787774]">
          <span class="tabular-nums">Página {{ page + 1 }} de {{ totalPages || 1 }}</span>
          <div class="flex gap-2">
            <button type="button" (click)="prev()" [disabled]="page === 0"
                    class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-4 py-2 font-medium text-[#111111] transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#F7F6F3] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100">Anterior</button>
            <button type="button" (click)="next()" [disabled]="page + 1 >= totalPages"
                    class="cursor-pointer rounded-md bg-[#111111] px-4 py-2 font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100">Siguiente</button>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class AdminUsersComponent implements OnInit {
  private service = inject(AdminUserService);
  private auth = inject(AuthService);

  readonly roles: Role[] = ['CUSTOMER', 'PROVIDER', 'ADMIN'];
  readonly pageSizes = [10, 20, 50];
  readonly sortOptions = [
    { value: 'createdAt,desc', label: 'Más recientes' },
    { value: 'createdAt,asc', label: 'Más antiguos' },
    { value: 'name,asc', label: 'Nombre (A-Z)' },
    { value: 'email,asc', label: 'Correo (A-Z)' },
    { value: 'role,asc', label: 'Rol' },
    { value: 'status,asc', label: 'Estado' },
  ];

  users: AdminUser[] = [];
  page = 0;
  size = 10;
  sort = 'createdAt,desc';
  totalPages = 0;
  totalElements = 0;
  loading = false;
  error = '';
  notice = '';
  private myEmail = (this.auth.email() ?? '').toLowerCase();

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.service.list(this.page, this.size, this.sort).subscribe({
      next: p => {
        this.users = p.items;
        this.totalPages = p.totalPages;
        this.totalElements = p.totalElements;
        this.loading = false;
      },
      error: e => { this.loading = false; this.fail(e, 'No fue posible cargar los usuarios.'); },
    });
  }

  changeSort(value: string): void { this.sort = value; this.page = 0; this.load(); }
  changeSize(value: string): void { this.size = Number(value); this.page = 0; this.load(); }
  prev(): void { if (this.page > 0) { this.page--; this.load(); } }
  next(): void { if (this.page + 1 < this.totalPages) { this.page++; this.load(); } }

  isMe(u: AdminUser): boolean { return u.email.toLowerCase() === this.myEmail; }

  toggleStatus(u: AdminUser): void {
    const target: UserStatus = u.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    if (target === 'SUSPENDED' && !window.confirm(`¿Suspender la cuenta de ${u.name}? No podrá iniciar sesión.`)) return;
    this.run(this.service.changeStatus(u.id, target),
      `Cuenta de ${u.name} ${target === 'SUSPENDED' ? 'suspendida' : 'reactivada'}.`);
  }

  setRole(u: AdminUser, select: HTMLSelectElement): void {
    const role = select.value as Role;
    if (role === u.role) return;
    if (!window.confirm(`¿Cambiar el rol de ${u.name} a ${role}?`)) { select.value = u.role; return; }
    this.run(this.service.changeRole(u.id, role), `Rol de ${u.name} actualizado a ${role}.`,
      () => { select.value = u.role; });
  }

  private run(req: Observable<AdminUser>, okMessage: string, onError?: () => void): void {
    this.error = '';
    this.notice = '';
    req.subscribe({
      next: () => { this.notice = okMessage; this.load(); },
      error: e => { onError?.(); this.fail(e, 'No fue posible completar la operación.'); },
    });
  }

  private fail(e: HttpErrorResponse, fallback: string): void {
    this.notice = '';
    if (e.status === 403) this.error = 'No tienes permiso para realizar esta acción.';
    else if (e.status === 401) this.error = 'Tu sesión expiró. Inicia sesión de nuevo.';
    else this.error = e?.error?.detail || fallback;
  }
}
