import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { Category, CategoryService } from '../core/category.service';
import { Offering, OfferingPayload, OfferingService } from '../core/offering.service';
import { MentorshipResource, MentorshipResourceService, ResourcePayload, ResourceType } from '../core/mentorship-resource.service';
import { forkJoin } from 'rxjs';

@Component({
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  template: `
    <section class="min-h-screen bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto max-w-4xl">

        <!-- Encabezado: una sola idea, una sola acción -->
        <header class="enter-rise flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">
              {{ isAdmin ? 'Todos los servicios' : 'Mis servicios' }}
            </h1>
            <p class="mt-4 text-base text-[#787774]">
              {{ offerings.length }} {{ offerings.length === 1 ? 'servicio' : 'servicios' }}
              · {{ activeCount }} {{ activeCount === 1 ? 'activo' : 'activos' }}
            </p>
          </div>

          <button type="button" (click)="openNew()"
                  class="inline-flex cursor-pointer items-center gap-2 self-start rounded-md bg-[#111111] px-4 py-2.5 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] motion-reduce:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111] sm:self-auto">
            <svg class="h-3.5 w-3.5" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
              <path d="M224 128a8 8 0 0 1-8 8h-80v80a8 8 0 0 1-16 0v-80H40a8 8 0 0 1 0-16h80V40a8 8 0 0 1 16 0v80h80a8 8 0 0 1 8 8Z"/>
            </svg>
            Nuevo servicio
          </button>
        </header>

        @if (error && !panelOpen && !deleteModalOpen) {
          <div role="alert" class="enter-rise mt-8 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">
            {{ error }}
          </div>
        }

        <!-- Lista editorial: filas separadas por una línea, sin tarjetas -->
        <ul class="mt-14 border-t border-[#EAEAEA]">
          @for (o of offerings; track o.id; let i = $index) {
            <li class="enter-rise flex flex-col gap-4 border-b border-[#EAEAEA] py-6 sm:flex-row sm:items-center sm:gap-8"
                [style.animation-delay.ms]="(i < 8 ? i : 8) * 40">

              <div class="min-w-0 flex-1 transition-opacity duration-200" [class.opacity-50]="!o.active">
                <h3 class="truncate text-lg font-medium tracking-[-0.01em] text-[#111111]">{{ o.title }}</h3>
                <p class="mt-1 truncate text-sm text-[#787774]">{{ o.description }}</p>
              </div>

              <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
                <span class="rounded-md bg-[#E1F3FE] px-2 py-0.5 text-xs font-medium text-[#1F6C9F]"
                      [class.opacity-50]="!o.active">{{ o.category }}</span>

                <span class="w-28 text-sm font-medium text-[#111111] tabular-nums sm:text-right"
                      [class.opacity-50]="!o.active">
                  {{ o.price | currency:'COP':'symbol-narrow':'1.0-0' }}
                </span>

                <!-- Interruptor activo / inactivo -->
                <button type="button" role="switch" (click)="toggle(o)"
                        [attr.aria-checked]="o.active"
                        [attr.aria-label]="(o.active ? 'Desactivar ' : 'Activar ') + o.title"
                        [class]="o.active ? 'bg-[#111111]' : 'bg-[#DADAD7]'"
                        class="relative h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-[cubic-bezier(0.23,1,0.32,1)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111]">
                  <span class="absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-white transition-transform duration-200 ease-[cubic-bezier(0.23,1,0.32,1)] motion-reduce:transition-none"
                        [class.translate-x-4]="o.active"></span>
                </button>

                <button type="button" (click)="edit(o)"
                        class="cursor-pointer rounded-md px-2.5 py-1.5 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100">
                  Editar
                </button>
                <button type="button" (click)="openResources(o)"
                        class="cursor-pointer rounded-md px-2.5 py-1.5 text-sm font-medium text-[#787774] transition-[background-color,color] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111]">
                  Recursos
                </button>
              </div>
            </li>
          } @empty {
            <li class="py-24 text-center">
              <p class="font-display text-3xl text-[#111111]">
                {{ offeringsLoading ? 'Cargando servicios…' : 'Aún no hay servicios' }}
              </p>
              @if (!offeringsLoading) {
                <p class="mx-auto mt-3 max-w-sm text-sm text-[#787774]">Crea el primero y aparecerá aquí, listo para recibir reservas.</p>
              }
            </li>
          }
        </ul>

        @if (resourceOffering) {
          <section class="mt-12 rounded-lg border border-[#EAEAEA] bg-white p-6">
            <div class="flex items-start justify-between gap-4"><div><h2 class="font-display text-3xl text-[#111111]">Recursos</h2><p class="mt-1 text-sm text-[#787774]">{{ resourceOffering.title }} · solo estudiantes con mentoría pagada y vigente podrán acceder.</p></div><button type="button" (click)="closeResources()" class="rounded px-2 py-1 text-sm text-[#787774] hover:bg-[#F1F0ED]">Cerrar</button></div>
            <form class="mt-6 grid gap-3 sm:grid-cols-[1fr_140px_1fr_auto]" (ngSubmit)="addResource()">
              <input name="resourceTitle" [(ngModel)]="resourceForm.title" class="rounded-md border border-[#EAEAEA] px-3 py-2 text-sm" placeholder="Título del recurso" required>
              <select name="resourceType" [(ngModel)]="resourceForm.resourceType" class="rounded-md border border-[#EAEAEA] px-3 py-2 text-sm"><option value="VIDEO">Video</option><option value="DOCUMENT">Documento</option><option value="LINK">Enlace</option></select>
              <input type="url" name="resourceUrl" [(ngModel)]="resourceForm.url" class="rounded-md border border-[#EAEAEA] px-3 py-2 text-sm" placeholder="https://..." required>
              <button [disabled]="resourceLoading" class="rounded-md bg-[#111] px-4 py-2 text-sm text-white disabled:opacity-40">Añadir</button>
            </form>
            @if (resourceError) { <p class="mt-3 text-sm text-[#9F2F2D]">{{ resourceError }}</p> }
            <ul class="mt-5 divide-y divide-[#EAEAEA]">@for (resource of resources; track resource.id) {<li class="flex items-center justify-between gap-4 py-3"><a [href]="resource.url" target="_blank" rel="noopener" class="min-w-0 truncate text-sm text-[#111] underline">{{ resource.title }}</a><span class="text-xs text-[#787774]">{{ resource.resourceType }}</span><button type="button" (click)="removeResource(resource)" class="text-sm text-[#9F2F2D]">Eliminar</button></li>} @empty {<li class="py-4 text-sm text-[#787774]">Todavía no has añadido recursos.</li>}</ul>
          </section>
        }

        <!-- Categorías: secundario, compacto, al final -->
        <div class="mt-20">
          <h2 class="text-sm font-medium text-[#111111]">Categorías</h2>

          <div class="mt-4 flex flex-wrap gap-2">
            @for (category of categories; track category.id) {
              <div class="group inline-flex items-center gap-1.5 rounded-md bg-[#F1F0ED] px-3 py-1.5 text-sm text-[#2F3437] transition-colors duration-150 hover:bg-[#E9E8E4]">
                <span>{{ category.name }}</span>
                <button type="button" (click)="startEditCategory(category)" aria-label="Editar categoría"
                        class="cursor-pointer rounded p-0.5 text-[#787774] transition-colors duration-150 hover:bg-[#DFDEDA] hover:text-[#111111]">
                  <svg class="h-3.5 w-3.5" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                    <path d="M227.31 73.37l-44.68-44.68a16 16 0 0 0-22.63 0L36.69 152.69a16 16 0 0 0-4.69 11.31V208a16 16 0 0 0 16 16h44a16 16 0 0 0 11.31-4.69l123.68-123.68a16 16 0 0 0 0-22.62zM68.68 192H48v-20.68l96-96L164.68 96z"/>
                  </svg>
                </button>
                <button type="button" (click)="promptDeleteCategory(category)" aria-label="Eliminar categoría"
                        class="cursor-pointer rounded p-0.5 text-[#787774] transition-colors duration-150 hover:bg-[#FDEBEC] hover:text-[#9F2F2D]">
                  <svg class="h-3.5 w-3.5" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                    <path d="M205.66 194.34a8 8 0 0 1-11.32 11.32L128 139.31l-66.34 66.35a8 8 0 0 1-11.32-11.32L116.69 128 50.34 61.66a8 8 0 0 1 11.32-11.32L128 116.69l66.34-66.35a8 8 0 0 1 11.32 11.32L139.31 128Z"/>
                  </svg>
                </button>
              </div>
            } @empty {
              <p class="text-sm text-[#787774]">{{ categoriesLoading ? 'Cargando categorías…' : 'Todavía no hay categorías.' }}</p>
            }
          </div>

          <form class="mt-5 flex max-w-sm gap-2" (ngSubmit)="saveCategory()">
            <input class="min-w-0 flex-1 rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                   name="newCategory" [(ngModel)]="newCategoryName"
                   [placeholder]="editingCategoryId ? 'Actualizar categoría' : 'Nueva categoría'" maxlength="80" required>
            
            @if (editingCategoryId) {
              <button type="button" (click)="cancelEditCategory()"
                      class="cursor-pointer rounded-md px-3 py-2 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100">
                Cancelar
              </button>
            }

            <button type="submit" [disabled]="categoryLoading"
                    class="cursor-pointer rounded-md px-4 py-2 text-sm font-medium text-[#111111] transition-[background-color,transform,opacity] duration-150 hover:bg-[#F1F0ED] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100">
              {{ categoryLoading ? 'Guardando…' : (editingCategoryId ? 'Actualizar' : 'Añadir') }}
            </button>
          </form>
        </div>
      </div>
    </section>

    <!-- Panel lateral de servicios -->
    <div (click)="closePanel()"
         class="fixed inset-0 z-40 bg-[#111111]/20 transition-opacity duration-300 ease-out"
         [class.opacity-100]="panelOpen" [class.opacity-0]="!panelOpen"
         [class.pointer-events-auto]="panelOpen" [class.pointer-events-none]="!panelOpen"></div>

    <aside class="fixed inset-y-0 right-0 z-50 w-full max-w-md border-l border-[#EAEAEA] bg-white shadow-[0_8px_40px_rgba(0,0,0,0.06)] transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] motion-reduce:transition-none"
           [class.translate-x-0]="panelOpen" [class.translate-x-full]="!panelOpen"
           [attr.inert]="panelOpen ? null : ''" [attr.aria-hidden]="!panelOpen"
           (keydown.escape)="closePanel()" role="dialog" aria-modal="true" aria-label="Formulario de servicio">

      <form class="flex h-full flex-col" (ngSubmit)="save()">
        <div class="flex items-center justify-between px-8 pb-2 pt-8">
          <h2 class="font-display text-3xl text-[#111111]">{{ editingId ? 'Editar servicio' : 'Nuevo servicio' }}</h2>
          <button type="button" (click)="reset()" aria-label="Cerrar"
                  class="cursor-pointer rounded-md p-2 text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.94] motion-reduce:active:scale-100">
            <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
              <path d="M205.66 194.34a8 8 0 0 1-11.32 11.32L128 139.31l-66.34 66.35a8 8 0 0 1-11.32-11.32L116.69 128 50.34 61.66a8 8 0 0 1 11.32-11.32L128 116.69l66.34-66.35a8 8 0 0 1 11.32 11.32L139.31 128Z"/>
            </svg>
          </button>
        </div>

        <div class="flex-1 space-y-7 overflow-y-auto px-8 py-6">

          @if (error) {
            <div role="alert" class="enter-rise rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">{{ error }}</div>
          }

          @if (isAdmin && !editingId) {
            <div>
              <label class="mb-2 block text-sm text-[#787774]">ID del proveedor</label>
              <input class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                     name="providerId" [(ngModel)]="form.providerId"
                     placeholder="UUID del proveedor dueño" required>
            </div>
          }

          <div>
            <label class="mb-2 block text-sm text-[#787774]">Título</label>
            <input class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                   name="title" [(ngModel)]="form.title" placeholder="Ej. Asesoría financiera" required>
          </div>

          @if (!editingId) {
            <div class="border-t border-[#EAEAEA] pt-6"><label class="mb-2 block text-sm text-[#787774]">Recursos iniciales (opcional)</label><div class="grid gap-2 sm:grid-cols-[1fr_130px_1fr_auto]"><input name="draftTitle" [(ngModel)]="draftResource.title" class="rounded-md border border-[#EAEAEA] px-3 py-2 text-sm" placeholder="Título"><select name="draftType" [(ngModel)]="draftResource.resourceType" class="rounded-md border border-[#EAEAEA] px-2 py-2 text-sm"><option value="VIDEO">Video</option><option value="DOCUMENT">Documento</option><option value="LINK">Enlace</option></select><input type="url" name="draftUrl" [(ngModel)]="draftResource.url" class="rounded-md border border-[#EAEAEA] px-3 py-2 text-sm" placeholder="https://..."><button type="button" (click)="queueResource()" class="rounded-md border px-3 text-sm">Agregar</button></div><ul class="mt-3 space-y-1 text-sm">@for (resource of draftResources; track resource.url) {<li class="flex justify-between gap-2"><span class="truncate">{{ resource.title }} · {{ resource.resourceType }}</span><button type="button" (click)="draftResources.splice($index, 1)" class="text-[#9F2F2D]">Quitar</button></li>}</ul></div>
          }
          <div>
            <label class="mb-2 block text-sm text-[#787774]">Categoría</label>
            <div class="flex flex-wrap gap-2">
              @for (category of categories; track category.id) {
                <button type="button" (click)="form.category = category.name"
                        [class]="form.category === category.name ? 'bg-[#111111] text-white' : 'bg-[#F1F0ED] text-[#2F3437] hover:bg-[#E9E8E4]'"
                        class="cursor-pointer rounded-md px-3 py-1.5 text-sm font-medium transition-[background-color,color,transform] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] active:scale-[0.97] motion-reduce:active:scale-100">
                  {{ category.name }}
                </button>
              }
            </div>
            @if (categories.length === 0) {
              <p class="mt-2 text-sm text-[#956400]">Crea primero una categoría en la página principal.</p>
            }
          </div>

          <div>
            <label class="mb-2 block text-sm text-[#787774]">Descripción</label>
            <textarea class="min-h-[110px] w-full resize-y rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm leading-6 text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                      name="description" [(ngModel)]="form.description" placeholder="Qué incluye el servicio" required></textarea>
          </div>

          <div>
            <label class="mb-2 block text-sm text-[#787774]">Precio (COP)</label>
            <input class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] tabular-nums outline-none transition-colors duration-150 placeholder:text-[#9B9A97] focus:border-[#111111]"
                   type="number" min="0" name="price" [(ngModel)]="form.price" placeholder="0" required>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 border-t border-[#EAEAEA] px-8 py-5">
          <button type="button" (click)="reset()"
                  class="cursor-pointer rounded-md px-4 py-2.5 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100">
            Cancelar
          </button>
          <button type="submit" [disabled]="loading || categories.length === 0"
                  class="cursor-pointer rounded-md bg-[#111111] px-5 py-2.5 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100">
            {{ loading ? 'Guardando…' : (editingId ? 'Guardar cambios' : 'Crear servicio') }}
          </button>
        </div>
      </form>
    </aside>

    <!-- Modal de confirmación de eliminación (A medida del diseño) -->
    <div (click)="closeDeleteModal()"
         class="fixed inset-0 z-40 bg-[#111111]/20 transition-opacity duration-300 ease-out"
         [class.opacity-100]="deleteModalOpen" [class.opacity-0]="!deleteModalOpen"
         [class.pointer-events-auto]="deleteModalOpen" [class.pointer-events-none]="!deleteModalOpen"></div>

    @if (deleteModalOpen) {
      <div class="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
        <div class="enter-modal pointer-events-auto w-full max-w-sm rounded-xl border border-[#EAEAEA] bg-white p-6 shadow-[0_8px_40px_rgba(0,0,0,0.06)]">
          <h3 class="font-display text-3xl text-[#111111]">Eliminar categoría</h3>
          <p class="mt-3 text-sm text-[#787774] leading-relaxed">
            ¿Estás seguro de que deseas eliminar <span class="font-medium text-[#111111]">"{{ categoryToDeleteName }}"</span>? Esta acción no se puede deshacer.
          </p>

          <div class="mt-7 flex items-center justify-end gap-2">
            <button type="button" (click)="closeDeleteModal()"
                    class="cursor-pointer rounded-md px-4 py-2.5 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100">
              Cancelar
            </button>
            <button type="button" (click)="executeDeleteCategory()"
                    class="cursor-pointer rounded-md bg-[#9F2F2D] px-5 py-2.5 text-sm font-medium text-white transition-[background-color,transform,opacity] duration-150 hover:bg-[#822523] active:scale-[0.98] motion-reduce:active:scale-100">
              Eliminar
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ProviderOfferingsComponent implements OnInit {
  private service = inject(OfferingService);
  private categoryService = inject(CategoryService);
  private auth = inject(AuthService);
  private resourceService = inject(MentorshipResourceService);

  offerings: Offering[] = [];
  categories: Category[] = [];
  form: Partial<OfferingPayload> = {};
  newCategoryName = '';
  editingId: string | null = null;
  editingCategoryId: string | null = null;
  
  // Estados para el modal de eliminación
  deleteModalOpen = false;
  categoryToDeleteId: string | null = null;
  categoryToDeleteName = '';

  loading = false;
  categoryLoading = false;
  offeringsLoading = true;
  categoriesLoading = true;
  error = '';
  resourceOffering: Offering | null = null;
  resources: MentorshipResource[] = [];
  resourceForm: ResourcePayload = { title: '', resourceType: 'LINK', url: '' };
  draftResource: ResourcePayload = { title: '', resourceType: 'LINK', url: '' };
  draftResources: ResourcePayload[] = [];
  resourceLoading = false;
  resourceError = '';

  panelOpen = false;

  get isAdmin(): boolean { return this.auth.hasRole('ADMIN'); }
  get activeCount(): number { return this.offerings.filter(o => o.active).length; }

  ngOnInit(): void {
    this.load();
    this.loadCategories();
  }

  load(): void {
    const req = this.isAdmin ? this.service.listAll() : this.service.listMine();
    req.subscribe({
      next: r => { this.offerings = r; this.offeringsLoading = false; },
      error: (e: HttpErrorResponse) => { this.offeringsLoading = false; this.fail(e, 'No fue posible cargar los servicios.'); },
    });
  }

  loadCategories(): void {
    this.categoryService.list().subscribe({
      next: r => { this.categories = r; this.categoriesLoading = false; },
      error: (e: HttpErrorResponse) => { this.categoriesLoading = false; this.fail(e, 'No fue posible cargar las categorías.'); },
    });
  }

  saveCategory(): void {
    this.error = '';
    const name = this.newCategoryName.trim();
    if (!name) {
      this.error = 'Escribe el nombre de la categoría.';
      return;
    }

    this.categoryLoading = true;

    if (this.editingCategoryId) {
      this.categoryService.update(this.editingCategoryId, { name }).subscribe({
        next: updated => {
          this.categories = this.categories.map(c => c.id === updated.id ? updated : c).sort((a, b) => a.name.localeCompare(b.name));
          this.cancelEditCategory();
          this.categoryLoading = false;
        },
        error: (e: HttpErrorResponse) => {
          this.categoryLoading = false;
          this.fail(e, 'No fue posible actualizar la categoría.');
        }
      });
    } else {
      this.categoryService.create({ name }).subscribe({
        next: category => {
          this.categories = [...this.categories, category].sort((a, b) => a.name.localeCompare(b.name));
          this.newCategoryName = '';
          this.categoryLoading = false;
        },
        error: (e: HttpErrorResponse) => {
          this.categoryLoading = false;
          this.fail(e, 'No fue posible crear la categoría.');
        },
      });
    }
  }

  startEditCategory(cat: Category): void {
    this.editingCategoryId = cat.id;
    this.newCategoryName = cat.name;
  }

  cancelEditCategory(): void {
    this.editingCategoryId = null;
    this.newCategoryName = '';
  }

  promptDeleteCategory(cat: Category): void {
    this.categoryToDeleteId = cat.id;
    this.categoryToDeleteName = cat.name;
    this.deleteModalOpen = true;
  }

  closeDeleteModal(): void {
    this.deleteModalOpen = false;
    this.categoryToDeleteId = null;
    this.categoryToDeleteName = '';
  }

  executeDeleteCategory(): void {
    if (!this.categoryToDeleteId) return;

    const id = this.categoryToDeleteId;
    this.categoryService.delete(id).subscribe({
      next: () => {
        this.categories = this.categories.filter(c => c.id !== id);
        this.closeDeleteModal();
      },
      error: (e: HttpErrorResponse) => {
        this.closeDeleteModal();
        this.fail(e, 'No fue posible eliminar la categoría.');
      }
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
      next: offering => {
        const createResources = !this.editingId && this.draftResources.length
          ? forkJoin(this.draftResources.map(resource => this.resourceService.create(offering.id, resource))) : null;
        if (createResources) {
          createResources.subscribe({ next: () => this.finishSave(), error: () => { this.loading = false; this.error = 'El servicio se creó, pero no fue posible guardar todos los recursos.'; this.load(); } });
        } else this.finishSave();
      },
      error: (e: HttpErrorResponse) => {
        this.loading = false;
        this.fail(e, 'No fue posible guardar el servicio.');
      },
    });
  }

  openNew(): void {
    this.error = '';
    this.form = {};
    this.editingId = null;
    this.draftResources = [];
    this.panelOpen = true;
  }

  closePanel(): void {
    this.reset();
  }

  edit(o: Offering): void {
    this.error = '';
    this.editingId = o.id;
    this.form = { title: o.title, description: o.description, category: o.category, price: o.price };
    this.panelOpen = true;
  }

  toggle(o: Offering): void {
    this.error = '';
    const req = o.active ? this.service.deactivate(o.id) : this.service.activate(o.id);
    req.subscribe({
      next: () => this.load(),
      error: (e: HttpErrorResponse) => this.fail(e, 'No fue posible cambiar el estado.'),
    });
  }

  reset(): void {
    this.form = {};
    this.editingId = null;
    this.panelOpen = false;
    this.draftResources = [];
  }

  openResources(offering: Offering): void { this.resourceOffering = offering; this.resourceError = ''; this.resourceForm = { title: '', resourceType: 'LINK', url: '' }; this.loadResources(); }
  closeResources(): void { this.resourceOffering = null; this.resources = []; }
  loadResources(): void { if (!this.resourceOffering) return; this.resourceService.listForOffering(this.resourceOffering.id).subscribe({ next: r => this.resources = r, error: e => this.resourceError = e?.error?.detail || 'No fue posible cargar los recursos.' }); }
  addResource(): void { if (!this.resourceOffering || !this.resourceForm.title.trim() || !this.resourceForm.url.trim()) return; this.resourceLoading = true; this.resourceError = ''; this.resourceService.create(this.resourceOffering.id, { ...this.resourceForm, title: this.resourceForm.title.trim(), url: this.resourceForm.url.trim() }).subscribe({ next: r => { this.resources = [...this.resources, r]; this.resourceForm = { title: '', resourceType: 'LINK', url: '' }; this.resourceLoading = false; }, error: e => { this.resourceLoading = false; this.resourceError = e?.error?.detail || 'No fue posible añadir el recurso.'; } }); }
  removeResource(resource: MentorshipResource): void { this.resourceService.delete(resource.id).subscribe({ next: () => this.resources = this.resources.filter(r => r.id !== resource.id), error: e => this.resourceError = e?.error?.detail || 'No fue posible eliminar el recurso.' }); }
  queueResource(): void { if (!this.draftResource.title.trim() || !this.draftResource.url.trim()) return; this.draftResources = [...this.draftResources, { ...this.draftResource, title: this.draftResource.title.trim(), url: this.draftResource.url.trim() }]; this.draftResource = { title: '', resourceType: 'LINK', url: '' }; }
  private finishSave(): void { this.loading = false; this.reset(); this.load(); }

  private fail(e: HttpErrorResponse, fallback: string): void {
    if (e.status === 403) this.error = 'No tienes permiso para realizar esta acción.';
    else if (e.status === 401) this.error = 'Tu sesión expiró. Inicia sesión de nuevo.';
    else this.error = e?.error?.detail || fallback;
  }
}
