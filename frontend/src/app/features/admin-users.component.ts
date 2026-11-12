import { Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService, Role } from '../core/auth.service';
import { AdminUser, AdminUserService, UserStatus } from '../core/admin-user.service';

@Component({
  standalone: true,
  template: `
    <section class="mx-auto max-w-6xl px-4 py-10">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 class="text-2xl font-bold text-slate-900">Usuarios</h1>
          <p class="mt-1 text-sm text-slate-500">{{ totalElements }} usuarios en total</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <select class="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                  (change)="changeSort($any($event.target).value)" aria-label="Ordenar por">
            @for (o of sortOptions; track o.value) {
              <option [value]="o.value" [selected]="o.value === sort">{{ o.label }}</option>
            }
          </select>
          <select class="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
                  (change)="changeSize($any($event.target).value)" aria-label="Usuarios por página">
            @for (s of pageSizes; track s) {
              <option [value]="s" [selected]="s === size">{{ s }} por página</option>
            }
          </select>
        </div>
      </div>

      @if (error) {
        <div role="alert" class="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{{ error }}</div>
      }
      @if (notice) {
        <div role="status" class="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{{ notice }}</div>
      }

      <div class="mt-6 overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
        <table class="min-w-full text-left text-sm">
          <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th class="px-4 py-3 font-semibold">Nombre</th>
              <th class="px-4 py-3 font-semibold">Correo</th>
              <th class="px-4 py-3 font-semibold">Rol</th>
              <th class="px-4 py-3 font-semibold">Estado</th>
              <th class="px-4 py-3 font-semibold">Acción</th>
            </tr>
          </thead>
          <tbody>
            @for (u of users; track u.id) {
              <tr class="border-b border-slate-100 last:border-0">
                <td class="px-4 py-3 font-medium text-slate-900">
                  {{ u.name }}
                  @if (isMe(u)) {
                    <span class="ml-2 rounded bg-blue-50 px-2 py-0.5 text-xs font-bold text-blue-800">Tú</span>
                  }
                </td>
                <td class="px-4 py-3 text-slate-600">{{ u.email }}</td>
                <td class="px-4 py-3">
                  <select class="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm disabled:cursor-not-allowed disabled:opacity-40"
                          [disabled]="isMe(u)" (change)="setRole(u, $any($event.target))" [attr.aria-label]="'Rol de ' + u.name">
                    @for (r of roles; track r) {
                      <option [value]="r" [selected]="r === u.role">{{ r }}</option>
                    }
                  </select>
                </td>
                <td class="px-4 py-3">
                  @if (u.status === 'ACTIVE') {
                    <span class="rounded bg-emerald-50 px-2 py-0.5 text-xs font-bold text-emerald-700">Activa</span>
                  } @else {
                    <span class="rounded bg-red-50 px-2 py-0.5 text-xs font-bold text-red-700">Suspendida</span>
                  }
                </td>
                <td class="px-4 py-3">
                  <button type="button" [disabled]="isMe(u)" (click)="toggleStatus(u)"
                          class="cursor-pointer rounded-lg border px-3 py-1.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-40"
                          [class.border-red-300]="u.status === 'ACTIVE'" [class.text-red-700]="u.status === 'ACTIVE'"
                          [class.border-emerald-300]="u.status === 'SUSPENDED'" [class.text-emerald-700]="u.status === 'SUSPENDED'">
                    {{ u.status === 'ACTIVE' ? 'Suspender' : 'Reactivar' }}
                  </button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="5" class="px-4 py-10 text-center text-slate-500">
                  {{ loading ? 'Cargando usuarios…' : 'No hay usuarios para mostrar.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="mt-4 flex items-center justify-between text-sm text-slate-600">
        <span>Página {{ page + 1 }} de {{ totalPages || 1 }}</span>
        <div class="flex gap-2">
          <button type="button" (click)="prev()" [disabled]="page === 0"
                  class="cursor-pointer rounded-lg border border-slate-300 px-3 py-1.5 font-semibold disabled:cursor-not-allowed disabled:opacity-40">Anterior</button>
          <button type="button" (click)="next()" [disabled]="page + 1 >= totalPages"
                  class="cursor-pointer rounded-lg border border-slate-300 px-3 py-1.5 font-semibold disabled:cursor-not-allowed disabled:opacity-40">Siguiente</button>
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
