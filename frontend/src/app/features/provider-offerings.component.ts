import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { Offering, OfferingPayload, OfferingService } from '../core/offering.service';

@Component({
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  template: `
    <section class="mx-auto max-w-3xl px-4 py-10">
      <h1 class="text-2xl font-bold text-slate-900">
        {{ isAdmin ? 'Todos los servicios (Admin)' : 'Mis servicios' }}
      </h1>

      @if (error) {
        <div role="alert" class="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{{ error }}</div>
      }

      <!-- Formulario crear / editar -->
      <form class="mt-6 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" (ngSubmit)="save()">
        <h2 class="text-lg font-semibold">{{ editingId ? 'Editar servicio' : 'Nuevo servicio' }}</h2>

        @if (isAdmin && !editingId) {
          <input class="w-full rounded-lg border border-slate-300 px-3 py-2" name="providerId"
                 [(ngModel)]="form.providerId" placeholder="providerId (UUID del Provider dueño)" required>
        }
        <input class="w-full rounded-lg border border-slate-300 px-3 py-2" name="title" [(ngModel)]="form.title" placeholder="Título" required>
        <input class="w-full rounded-lg border border-slate-300 px-3 py-2" name="category" [(ngModel)]="form.category" placeholder="Categoría" required>
        <input class="w-full rounded-lg border border-slate-300 px-3 py-2" name="description" [(ngModel)]="form.description" placeholder="Descripción" required>
        <input class="w-full rounded-lg border border-slate-300 px-3 py-2" type="number" min="0" name="price" [(ngModel)]="form.price" placeholder="Precio" required>

        <div class="flex gap-2">
          <button type="submit" [disabled]="loading"
                  class="cursor-pointer rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-900">
            {{ editingId ? 'Guardar cambios' : 'Crear' }}
          </button>
          @if (editingId) {
            <button type="button" (click)="reset()"
                    class="cursor-pointer rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700">Cancelar</button>
          }
        </div>
      </form>

      <!-- Listado -->
      <div class="mt-8 flex flex-col gap-4">
        @for (o of offerings; track o.id) {
          <article class="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between"
                   [class.opacity-60]="!o.active">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="font-bold text-slate-900">{{ o.title }}</h3>
                <span class="rounded bg-blue-50 px-2 py-0.5 text-xs font-bold text-blue-800">{{ o.category }}</span>
                @if (!o.active) { <span class="rounded bg-slate-200 px-2 py-0.5 text-xs font-bold text-slate-600">Inactivo</span> }
              </div>
              <p class="mt-1 text-sm text-slate-500">{{ o.description }}</p>
              <p class="mt-1 text-sm font-semibold">{{ o.price | currency:'COP':'symbol-narrow':'1.0-0' }}</p>
            </div>
            <div class="flex shrink-0 gap-2">
              <button type="button" (click)="edit(o)"
                      class="cursor-pointer rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-semibold">Editar</button>
              @if (o.active) {
                <button type="button" (click)="toggle(o)"
                        class="cursor-pointer rounded-lg border border-red-300 px-3 py-1.5 text-sm font-semibold text-red-700">Desactivar</button>
              } @else {
                <button type="button" (click)="toggle(o)"
                        class="cursor-pointer rounded-lg border border-emerald-300 px-3 py-1.5 text-sm font-semibold text-emerald-700">Activar</button>
              }
            </div>
          </article>
        } @empty {
          <p class="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center text-sm text-slate-500">
            No hay servicios todavía.
          </p>
        }
      </div>
    </section>
  `,
})
export class ProviderOfferingsComponent implements OnInit {
  private service = inject(OfferingService);
  private auth = inject(AuthService);

  offerings: Offering[] = [];
  form: Partial<OfferingPayload> = {};
  editingId: string | null = null;
  loading = false;
  error = '';

  get isAdmin(): boolean { return this.auth.hasRole('ADMIN'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    const req = this.isAdmin ? this.service.listAll() : this.service.listMine();
    req.subscribe({
      next: r => this.offerings = r,
      error: e => this.fail(e, 'No fue posible cargar los servicios.'),
    });
  }

  save(): void {
    this.error = '';
    const p = this.form as OfferingPayload;
    if (!p.title || !p.description || !p.category || p.price == null) { this.error = 'Completa todos los campos.'; return; }
    this.loading = true;
    const req = this.editingId ? this.service.update(this.editingId, p) : this.service.create(p);
    req.subscribe({
      next: () => { this.loading = false; this.reset(); this.load(); },
      error: e => { this.loading = false; this.fail(e, 'No fue posible guardar el servicio.'); },
    });
  }

  edit(o: Offering): void {
    this.editingId = o.id;
    this.form = { title: o.title, description: o.description, category: o.category, price: o.price };
  }

  toggle(o: Offering): void {
    this.error = '';
    const req = o.active ? this.service.deactivate(o.id) : this.service.activate(o.id);
    req.subscribe({ next: () => this.load(), error: e => this.fail(e, 'No fue posible cambiar el estado.') });
  }

  reset(): void { this.form = {}; this.editingId = null; }

  private fail(e: HttpErrorResponse, fallback: string): void {
    if (e.status === 403) this.error = 'No tienes permiso para realizar esta acción.';
    else if (e.status === 401) this.error = 'Tu sesión expiró. Inicia sesión de nuevo.';
    else this.error = e?.error?.detail || fallback;
  }
}
