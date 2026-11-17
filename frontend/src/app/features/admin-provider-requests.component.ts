import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ProviderRequest, ProviderRequestService } from '../core/provider-request.service';

@Component({
  selector: 'app-admin-provider-requests',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  template: `
    <section class="min-h-screen bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto max-w-5xl">

        <!-- Encabezado -->
        <header class="enter-rise flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">Solicitudes Provider</h1>
            <p class="mt-4 text-base text-[#787774] tabular-nums">
              {{ totalElements }} solicitudes pendientes de verificación
            </p>
          </div>

          <div class="flex flex-wrap gap-2">
            <select
              class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]"
              (change)="changeSort($any($event.target).value)"
              aria-label="Ordenar por"
            >
              @for (o of sortOptions; track o.value) {
                <option [value]="o.value" [selected]="o.value === sort">{{ o.label }}</option>
              }
            </select>
            <select
              class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-3 py-2 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]"
              (change)="changeSize($any($event.target).value)"
              aria-label="Solicitudes por página"
            >
              @for (s of pageSizes; track s) {
                <option [value]="s" [selected]="s === size">{{ s }} por página</option>
              }
            </select>
          </div>
        </header>

        <!-- Avisos de error o éxito -->
        @if (error) {
          <div role="alert" class="enter-rise mt-8 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">
            {{ error }}
          </div>
        }
        @if (notice) {
          <div role="status" class="enter-rise mt-8 rounded-lg bg-[#EDF3EC] px-4 py-3 text-sm text-[#346538]">
            {{ notice }}
          </div>
        }

        <!-- Tabla de solicitudes -->
        <div class="enter-rise mt-12 overflow-x-auto" style="animation-delay: 60ms">
          <table class="min-w-full text-left text-sm">
            <thead>
              <tr class="border-b border-[#EAEAEA] text-[#787774]">
                <th class="py-3 pr-4 font-medium">Solicitante</th>
                <th class="px-4 py-3 font-medium">Motivo / Propuesta</th>
                <th class="px-4 py-3 font-medium">Fecha</th>
                <th class="py-3 pl-4 text-right font-medium">Acción</th>
              </tr>
            </thead>
            <tbody>
              @for (r of requests; track r.id) {
                <tr class="border-b border-[#EAEAEA] transition-colors duration-150 hover:bg-[#F7F6F3]">
                  <td class="py-4 pr-4">
                    <p class="font-medium text-[#111111]">{{ r.userName || 'Usuario' }}</p>
                    <p class="text-xs text-[#787774]">{{ r.userEmail || r.userId }}</p>
                  </td>
                  <td class="max-w-xs px-4 py-4 text-[#111111]">
                    <p class="line-clamp-3 text-sm whitespace-pre-line">{{ r.message }}</p>
                  </td>
                  <td class="px-4 py-4 whitespace-nowrap text-[#787774] tabular-nums">
                    {{ r.createdAt | date:'d MMM y, HH:mm' }}
                  </td>
                  <td class="py-4 pl-4 text-right whitespace-nowrap">
                    <div class="flex items-center justify-end gap-2">
                      <button
                        type="button"
                        (click)="openApproveModal(r)"
                        class="cursor-pointer rounded-md bg-[#111111] px-3 py-1.5 text-xs font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] active:scale-[0.97] motion-reduce:active:scale-100"
                      >
                        Aprobar
                      </button>
                      <button
                        type="button"
                        (click)="openRejectModal(r)"
                        class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-3 py-1.5 text-xs font-medium text-[#9F2F2D] transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#FDEBEC] active:scale-[0.97] motion-reduce:active:scale-100"
                      >
                        Rechazar
                      </button>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4" class="py-24 text-center text-sm text-[#787774]">
                    {{ loading ? 'Cargando solicitudes...' : 'No hay solicitudes pendientes de Provider.' }}
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
            <button
              type="button"
              (click)="prev()"
              [disabled]="page === 0"
              class="cursor-pointer rounded-md border border-[#EAEAEA] bg-white px-4 py-2 font-medium text-[#111111] transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#F7F6F3] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100"
            >
              Anterior
            </button>
            <button
              type="button"
              (click)="next()"
              [disabled]="page + 1 >= totalPages"
              class="cursor-pointer rounded-md bg-[#111111] px-4 py-2 font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100"
            >
              Siguiente
            </button>
          </div>
        </div>

      </div>
    </section>

    <!-- Modal de confirmación Aprobar -->
    @if (selectedForApprove) {
      <div class="enter-fade fixed inset-0 z-50 flex items-center justify-center bg-[#111111]/25 p-4">
        <div class="enter-modal w-full max-w-md rounded-xl border border-[#EAEAEA] bg-white p-7 shadow-[0_8px_40px_rgba(0,0,0,0.08)]">
          <h3 class="font-display text-2xl text-[#111111]">Aprobar solicitud de Provider</h3>
          <p class="mt-2 text-sm text-[#787774]">
            Se otorgarán permisos de <strong>PROVIDER</strong> a {{ selectedForApprove.userName || selectedForApprove.userEmail }}.
          </p>

          <div class="mt-4">
            <label for="approveReason" class="block text-xs font-medium uppercase tracking-wide text-[#787774]">
              Nota administrativa (opcional):
            </label>
            <input
              id="approveReason"
              type="text"
              maxlength="500"
              [(ngModel)]="decisionReason"
              placeholder="Ej: Documentación verificada"
              class="mt-1 block w-full rounded-md border border-[#EAEAEA] p-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]"
            />
          </div>

          <div class="mt-6 flex justify-end gap-2">
            <button
              type="button"
              (click)="selectedForApprove = null"
              [disabled]="processing"
              class="cursor-pointer rounded-md px-4 py-2 text-sm font-medium text-[#787774] transition-[background-color,color] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111]"
            >
              Cancelar
            </button>
            <button
              type="button"
              (click)="confirmApprove()"
              [disabled]="processing"
              class="cursor-pointer rounded-md bg-[#111111] px-4 py-2 text-sm font-medium text-white transition-[background-color] duration-150 hover:bg-[#333333] disabled:opacity-50"
            >
              {{ processing ? 'Aprobando...' : 'Confirmar aprobación' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- Modal de confirmación Rechazar -->
    @if (selectedForReject) {
      <div class="enter-fade fixed inset-0 z-50 flex items-center justify-center bg-[#111111]/25 p-4">
        <div class="enter-modal w-full max-w-md rounded-xl border border-[#EAEAEA] bg-white p-7 shadow-[0_8px_40px_rgba(0,0,0,0.08)]">
          <h3 class="font-display text-2xl text-[#9F2F2D]">Rechazar solicitud de Provider</h3>
          <p class="mt-2 text-sm text-[#787774]">
            El usuario mantendrá su rol de Customer sin capacidades de Provider.
          </p>

          <div class="mt-4">
            <label for="rejectReason" class="block text-xs font-medium uppercase tracking-wide text-[#787774]">
              Motivo del rechazo (opcional):
            </label>
            <input
              id="rejectReason"
              type="text"
              maxlength="500"
              [(ngModel)]="decisionReason"
              placeholder="Ej: Faltan datos en la descripción de servicios"
              class="mt-1 block w-full rounded-md border border-[#EAEAEA] p-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]"
            />
          </div>

          <div class="mt-6 flex justify-end gap-2">
            <button
              type="button"
              (click)="selectedForReject = null"
              [disabled]="processing"
              class="cursor-pointer rounded-md px-4 py-2 text-sm font-medium text-[#787774] transition-[background-color,color] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111]"
            >
              Cancelar
            </button>
            <button
              type="button"
              (click)="confirmReject()"
              [disabled]="processing"
              class="cursor-pointer rounded-md bg-[#9F2F2D] px-4 py-2 text-sm font-medium text-white transition-[background-color] duration-150 hover:bg-[#862624] disabled:opacity-50"
            >
              {{ processing ? 'Rechazando...' : 'Confirmar rechazo' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class AdminProviderRequestsComponent implements OnInit {
  private service = inject(ProviderRequestService);
  auth = inject(AuthService);

  readonly pageSizes = [10, 20, 50];
  readonly sortOptions = [
    { value: 'createdAt,asc', label: 'Más antiguos (FIFO)' },
    { value: 'createdAt,desc', label: 'Más recientes' },
  ];

  requests: ProviderRequest[] = [];
  page = 0;
  size = 10;
  sort = 'createdAt,asc';
  totalPages = 0;
  totalElements = 0;
  loading = false;
  processing = false;
  error = '';
  notice = '';

  selectedForApprove: ProviderRequest | null = null;
  selectedForReject: ProviderRequest | null = null;
  decisionReason = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.service.listPending(this.page, this.size, this.sort).subscribe({
      next: (p) => {
        this.requests = p.items;
        this.totalPages = p.totalPages;
        this.totalElements = p.totalElements;
        this.loading = false;
      },
      error: (e: HttpErrorResponse) => {
        this.loading = false;
        this.error = e?.error?.detail || 'No fue posible cargar las solicitudes de Provider.';
      },
    });
  }

  changeSort(value: string): void {
    this.sort = value;
    this.page = 0;
    this.load();
  }

  changeSize(value: string): void {
    this.size = Number(value);
    this.page = 0;
    this.load();
  }

  prev(): void {
    if (this.page > 0) {
      this.page--;
      this.load();
    }
  }

  next(): void {
    if (this.page + 1 < this.totalPages) {
      this.page++;
      this.load();
    }
  }

  openApproveModal(r: ProviderRequest): void {
    this.selectedForApprove = r;
    this.selectedForReject = null;
    this.decisionReason = '';
  }

  openRejectModal(r: ProviderRequest): void {
    this.selectedForReject = r;
    this.selectedForApprove = null;
    this.decisionReason = '';
  }

  confirmApprove(): void {
    if (!this.selectedForApprove) return;
    const req = this.selectedForApprove;
    this.processing = true;
    this.error = '';
    this.notice = '';

    this.service.approve(req.id, this.decisionReason.trim()).subscribe({
      next: () => {
        this.processing = false;
        this.selectedForApprove = null;
        this.notice = `Solicitud de ${req.userName || req.userEmail} aprobada exitosamente. Ahora es Provider.`;
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.processing = false;
        this.error = e?.error?.detail || 'No fue posible aprobar la solicitud.';
      },
    });
  }

  confirmReject(): void {
    if (!this.selectedForReject) return;
    const req = this.selectedForReject;
    this.processing = true;
    this.error = '';
    this.notice = '';

    this.service.reject(req.id, this.decisionReason.trim()).subscribe({
      next: () => {
        this.processing = false;
        this.selectedForReject = null;
        this.notice = `Solicitud de ${req.userName || req.userEmail} rechazada.`;
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.processing = false;
        this.error = e?.error?.detail || 'No fue posible rechazar la solicitud.';
      },
    });
  }
}
