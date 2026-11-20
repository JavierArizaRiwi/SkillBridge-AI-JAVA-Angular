import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { Category, CategoryService } from '../core/category.service';
import { Offering, OfferingPayload, OfferingService } from '../core/offering.service';

@Component({
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  template: `
    <section class="min-h-screen bg-slate-50 px-4 py-8 sm:px-6 lg:px-8">
      <div class="mx-auto max-w-7xl">
        <!-- Header con mejor jerarquía -->
        <div class="mb-8 flex flex-col gap-2">
          <div class="flex items-center gap-2">
            <span class="inline-flex items-center rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-blue-700">
              Gestión de catálogo
            </span>
          </div>
          <h1 class="text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            {{ isAdmin ? 'Todos los servicios (Admin)' : 'Mis servicios' }}
          </h1>
          <p class="max-w-2xl text-sm text-slate-500">
            Crea y administra servicios usando las categorías disponibles con una experiencia fluida y organizada.
          </p>
        </div>

        @if (error) {
          <div role="alert" class="mb-6 flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 shadow-sm">
            <svg class="h-5 w-5 shrink-0 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
            </svg>
            <span>{{ error }}</span>
          </div>
        }

        <div class="grid items-start gap-8 lg:grid-cols-2">
          <!-- Columna izquierda: Formularios -->
          <div class="flex flex-col gap-6">
            <!-- Formulario de Servicio -->
            <form class="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm transition-all hover:shadow-md" (ngSubmit)="save()">
              <div class="mb-5 border-b border-slate-100 pb-4">
                <h2 class="text-lg font-bold text-slate-900">
                  {{ editingId ? 'Editar servicio' : 'Nuevo servicio' }}
                </h2>
                <p class="mt-1 text-xs text-slate-500">Completa los campos necesarios para actualizar el catálogo.</p>
              </div>

              @if (isAdmin && !editingId) {
                <div class="mb-4">
                  <label class="mb-1.5 block text-xs font-semibold text-slate-700">ID del Proveedor</label>
                  <input class="w-full rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100"
                         name="providerId" [(ngModel)]="form.providerId"
                         placeholder="UUID del Provider dueño" required>
                </div>
              }

              <div class="grid gap-4 sm:grid-cols-2">
                <div>
                  <label class="mb-1.5 block text-xs font-semibold text-slate-700">Título del servicio</label>
                  <input class="w-full rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100"
                         name="title" [(ngModel)]="form.title" placeholder="Ej. Asesoría Financiera" required>
                </div>

                <!-- Selector personalizado con scroll en las categorías -->
                <div>
                  <label class="mb-1.5 block text-xs font-semibold text-slate-700">Categoría</label>
                  <div class="relative w-full" tabindex="0" (blur)="categoryDropdownOpen = false">
                    <button type="button" 
                            (click)="categoryDropdownOpen = !categoryDropdownOpen"
                            class="flex w-full items-center justify-between rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-2.5 text-left text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100">
                      <span class="{{ form.category ? 'text-slate-900 font-medium' : 'text-slate-400' }}">
                        {{ form.category || 'Selecciona una categoría' }}
                      </span>
                      <svg class="h-4 w-4 text-slate-400 transition-transform duration-200" [class.rotate-180]="categoryDropdownOpen" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                      </svg>
                    </button>

                    @if (categoryDropdownOpen) {
                      <div class="absolute z-20 mt-1.5 max-h-48 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white p-1 shadow-xl">
                        <div class="cursor-pointer rounded-lg px-3 py-2 text-xs text-slate-400 hover:bg-slate-50"
                             (click)="form.category = ''; categoryDropdownOpen = false">
                          Ninguna / Limpiar selección
                        </div>
                        @for (category of categories; track category.id) {
                          <div class="cursor-pointer rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-blue-50 hover:text-blue-900"
                               (click)="form.category = category.name; categoryDropdownOpen = false">
                            {{ category.name }}
                          </div>
                        }
                      </div>
                    }
                  </div>
                </div>
              </div>

              @if (categories.length === 0) {
                <p class="mt-2 text-xs font-medium text-amber-600">
                  ⚠️ No hay categorías disponibles. Crea una categoría en el bloque inferior.
                </p>
              }

              <div class="mt-4">
                <label class="mb-1.5 block text-xs font-semibold text-slate-700">Descripción detallada</label>
                <textarea class="min-h-[110px] w-full resize-y rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100"
                          name="description" [(ngModel)]="form.description" placeholder="Describe brevemente lo que incluye el servicio..." required></textarea>
              </div>

              <div class="mt-4">
                <label class="mb-1.5 block text-xs font-semibold text-slate-700">Precio (COP)</label>
                <input class="w-full rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100"
                       type="number" min="0" name="price" [(ngModel)]="form.price" placeholder="0" required>
              </div>

              <div class="mt-6 flex flex-wrap items-center gap-3">
                <button type="submit" [disabled]="loading || categories.length === 0"
                        class="cursor-pointer rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-blue-900 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50">
                  {{ editingId ? 'Guardar cambios' : 'Crear servicio' }}
                </button>
                @if (editingId) {
                  <button type="button" (click)="reset()"
                          class="cursor-pointer rounded-xl border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50">
                    Cancelar
                  </button>
                }
              </div>
            </form>

            <!-- Formulario de Categoría -->
            <form class="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm transition-all hover:shadow-md" (ngSubmit)="createCategory()">
              <div class="mb-4">
                <h2 class="text-base font-bold text-slate-900">Crear nueva categoría</h2>
                <p class="mt-0.5 text-xs text-slate-500">Quedará disponible de inmediato en el selector superior.</p>
              </div>

              <div class="flex flex-col gap-3 sm:flex-row">
                <input class="min-w-0 flex-1 rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100"
                       name="newCategory" [(ngModel)]="newCategoryName"
                       placeholder="Ej. Marketing Digital" maxlength="80" required>
                <button type="submit" [disabled]="categoryLoading"
                        class="cursor-pointer rounded-xl bg-blue-700 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-blue-800 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50">
                  {{ categoryLoading ? 'Creando...' : 'Crear categoría' }}
                </button>
              </div>
            </form>
          </div>

          <!-- Columna derecha: Listados con Scroll optimizado -->
          <div class="flex flex-col gap-6">
            <!-- Listado de Servicios -->
            <section class="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm">
              <div class="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
                <h2 class="text-base font-bold text-slate-900">Servicios creados</h2>
                <span class="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-bold text-slate-600">
                  {{ offerings.length }}
                </span>
              </div>

              <div class="max-h-[520px] space-y-3 overflow-y-auto pr-1">
                @for (o of offerings; track o.id) {
                  <article class="flex flex-col gap-3 rounded-xl border border-slate-200/70 bg-slate-50/50 p-4 transition-all hover:bg-white hover:shadow-sm sm:flex-row sm:items-center sm:justify-between"
                           [class.opacity-60]="!o.active">
                    <div class="min-w-0 flex-1">
                      <div class="flex flex-wrap items-center gap-2">
                        <h3 class="font-bold text-slate-900">{{ o.title }}</h3>
                        <span class="rounded-md bg-blue-50 px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wide text-blue-800">{{ o.category }}</span>
                        @if (!o.active) {
                          <span class="rounded-md bg-slate-200 px-2 py-0.5 text-[10px] font-bold text-slate-600">Inactivo</span>
                        }
                      </div>
                      <p class="mt-1 line-clamp-2 text-xs text-slate-500">{{ o.description }}</p>
                      <p class="mt-1.5 text-xs font-bold text-slate-900">{{ o.price | currency:'COP':'symbol-narrow':'1.0-0' }}</p>
                    </div>

                    <div class="flex shrink-0 gap-2">
                      <button type="button" (click)="edit(o)"
                              class="cursor-pointer rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50">
                        Editar
                      </button>
                      @if (o.active) {
                        <button type="button" (click)="toggle(o)"
                                class="cursor-pointer rounded-lg border border-red-200 bg-white px-3 py-1.5 text-xs font-semibold text-red-600 shadow-sm transition hover:bg-red-50">
                          Desactivar
                        </button>
                      } @else {
                        <button type="button" (click)="toggle(o)"
                                class="cursor-pointer rounded-lg border border-emerald-200 bg-white px-3 py-1.5 text-xs font-semibold text-emerald-700 shadow-sm transition hover:bg-emerald-50">
                          Activar
                        </button>
                      }
                    </div>
                  </article>
                } @empty {
                  <div class="rounded-xl border border-dashed border-slate-200 px-6 py-12 text-center">
                    <p class="text-sm text-slate-400">No hay servicios registrados todavía.</p>
                  </div>
                }
              </div>
            </section>

            <!-- Listado de Categorías -->
            <section class="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm">
              <div class="mb-4 flex items-center justify-between border-b border-slate-100 pb-3">
                <h2 class="text-base font-bold text-slate-900">Categorías disponibles</h2>
                <span class="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-bold text-slate-600">
                  {{ categories.length }}
                </span>
              </div>

              <div class="max-h-[240px] space-y-2 overflow-y-auto pr-1">
                @for (category of categories; track category.id) {
                  <div class="flex items-center justify-between rounded-xl border border-slate-200/70 bg-slate-50/50 px-4 py-2.5 transition hover:bg-white">
                    <span class="text-sm font-medium text-slate-800">{{ category.name }}</span>
                    <span class="inline-flex items-center gap-1.5 text-[11px] font-semibold text-emerald-600">
                      <span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span> Activa
                    </span>
                  </div>
                } @empty {
                  <div class="rounded-xl border border-dashed border-slate-200 px-4 py-8 text-center">
                    <p class="text-xs text-slate-400">No hay categorías registradas.</p>
                  </div>
                }
              </div>
            </section>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class ProviderOfferingsComponent implements OnInit {
  private service = inject(OfferingService);
  private categoryService = inject(CategoryService);
  private auth = inject(AuthService);

  offerings: Offering[] = [];
  categories: Category[] = [];
  form: Partial<OfferingPayload> = {};
  newCategoryName = '';
  editingId: string | null = null;
  loading = false;
  categoryLoading = false;
  error = '';

  // Control de estado para el desplegable con scroll de categorías
  categoryDropdownOpen = false;

  get isAdmin(): boolean { return this.auth.hasRole('ADMIN'); }

  ngOnInit(): void {
    this.load();
    this.loadCategories();
  }

  load(): void {
    const req = this.isAdmin ? this.service.listAll() : this.service.listMine();
    req.subscribe({
      next: r => this.offerings = r,
      error: e => this.fail(e, 'No fue posible cargar los servicios.'),
    });
  }

  loadCategories(): void {
    this.categoryService.list().subscribe({
      next: r => this.categories = r,
      error: e => this.fail(e, 'No fue posible cargar las categorías.'),
    });
  }

  save(): void {
    this.error = '';
    const p = this.form as OfferingPayload;
    if (!p.title || !p.description || !p.category || p.price == null) {
      this.error = 'Completa todos los campos obligatorios del servicio.';
      return;
    }

    this.loading = true;
    const req = this.editingId ? this.service.update(this.editingId, p) : this.service.create(p);
    req.subscribe({
      next: () => {
        this.loading = false;
        this.reset();
        this.load();
      },
      error: e => {
        this.loading = false;
        this.fail(e, 'No fue posible guardar el servicio.');
      },
    });
  }

  createCategory(): void {
    this.error = '';
    const name = this.newCategoryName.trim();
    if (!name) {
      this.error = 'Escribe el nombre de la categoría.';
      return;
    }

    this.categoryLoading = true;
    this.categoryService.create({ name }).subscribe({
      next: category => {
        this.categories = [...this.categories, category].sort((a, b) => a.name.localeCompare(b.name));
        this.form.category = category.name;
        this.newCategoryName = '';
        this.categoryLoading = false;
      },
      error: e => {
        this.categoryLoading = false;
        this.fail(e, 'No fue posible crear la categoría.');
      },
    });
  }

  edit(o: Offering): void {
    this.editingId = o.id;
    this.form = { title: o.title, description: o.description, category: o.category, price: o.price };
  }

  toggle(o: Offering): void {
    this.error = '';
    const req = o.active ? this.service.deactivate(o.id) : this.service.activate(o.id);
    req.subscribe({
      next: () => this.load(),
      error: e => this.fail(e, 'No fue posible cambiar el estado.'),
    });
  }

  reset(): void {
    this.form = {};
    this.editingId = null;
  }

  private fail(e: HttpErrorResponse, fallback: string): void {
    if (e.status === 403) this.error = 'No tienes permiso para realizar esta acción.';
    else if (e.status === 401) this.error = 'Tu sesión expiró. Inicia sesión de nuevo.';
    else this.error = e?.error?.detail || fallback;
  }
}
