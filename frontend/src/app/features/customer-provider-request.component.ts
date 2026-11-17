import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ProviderRequest, ProviderRequestService } from '../core/provider-request.service';

@Component({
  selector: 'app-customer-provider-request',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, RouterLink],
  template: `
    <section class="min-h-screen bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto max-w-3xl">

        <!-- Encabezado -->
        <header class="enter-rise">
          <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">Cuenta Provider</h1>
          <p class="mt-4 text-base text-[#787774]">
            Solicita la verificación para publicar y ofrecer tus propios servicios en SkillBridge AI.
          </p>
        </header>

        <!-- Mensajes de estado -->
        @if (error) {
          <div role="alert" class="enter-rise mt-8 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">{{ error }}</div>
        }
        @if (notice) {
          <div role="status" class="enter-rise mt-8 rounded-lg bg-[#EDF3EC] px-4 py-3 text-sm text-[#346538]">{{ notice }}</div>
        }

        <!-- Caso 1: El usuario ya es PROVIDER o ADMIN -->
        @if (isProviderOrAdmin) {
          <div class="enter-rise mt-10 rounded-xl border border-[#EAEAEA] bg-white p-8">
            <span class="rounded-md bg-[#EDF3EC] px-2.5 py-1 text-xs font-medium text-[#346538]">
              Cuenta verificada
            </span>
            <h2 class="mt-4 font-display text-2xl text-[#111111]">Ya tienes capacidades de Provider</h2>
            <p class="mt-2 text-sm text-[#787774]">
              Tu cuenta tiene rol <strong class="text-[#111111]">{{ auth.role() }}</strong>. Puedes crear y gestionar tus servicios directamente.
            </p>
            <div class="mt-6">
              <a routerLink="/provider/offerings"
                 class="inline-block rounded-md bg-[#111111] px-5 py-2.5 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] active:scale-[0.98]">
                Ir a Mis Servicios
              </a>
            </div>
          </div>
        } @else {

          <!-- Spinner mientras carga -->
          @if (loading) {
            <div class="flex flex-col items-center justify-center py-20">
              <div class="h-6 w-6 animate-spin rounded-full border-2 border-[#EAEAEA] border-t-[#111111] [animation-duration:600ms]"></div>
              <p class="mt-3 text-sm text-[#787774]">Cargando estado de tu solicitud...</p>
            </div>
          } @else {

            <!-- Caso 2: Tiene solicitud pendiente -->
            @if (pendingRequest) {
              <div class="enter-rise mt-10 rounded-xl border border-[#EAEAEA] bg-white p-8">
                <div class="flex items-center justify-between">
                  <span class="rounded-md bg-[#FBF3DB] px-2.5 py-1 text-xs font-medium text-[#795A00]">
                    En revisión (PENDIENTE)
                  </span>
                  <span class="text-xs text-[#787774] tabular-nums">
                    Enviada el {{ pendingRequest.createdAt | date:'d MMM y, HH:mm' }}
                  </span>
                </div>

                <h2 class="mt-4 font-display text-2xl text-[#111111]">Tu solicitud está en evaluación</h2>
                <p class="mt-2 text-sm leading-6 text-[#787774]">
                  Un administrador de SkillBridge AI está revisando tu propuesta. Cuando sea aprobada, tus capacidades de Provider se activarán de inmediato.
                </p>

                <div class="mt-6 rounded-lg bg-[#F7F6F3] p-4">
                  <p class="text-xs font-medium uppercase tracking-wide text-[#787774]">Tu motivo / propuesta:</p>
                  <p class="mt-1 text-sm text-[#111111] whitespace-pre-line">{{ pendingRequest.message }}</p>
                </div>
              </div>
            }

            <!-- Caso 3: Solicitud rechazada anteriormente (muestra aviso y permite reenviar) -->
            @if (!pendingRequest && lastRejectedRequest) {
              <div class="enter-rise mt-10 rounded-xl border border-[#FDEBEC] bg-[#FFFBFB] p-6">
                <div class="flex items-center justify-between">
                  <span class="rounded-md bg-[#FDEBEC] px-2.5 py-1 text-xs font-medium text-[#9F2F2D]">
                    Solicitud rechazada
                  </span>
                  @if (lastRejectedRequest.decidedAt) {
                    <span class="text-xs text-[#787774] tabular-nums">
                      {{ lastRejectedRequest.decidedAt | date:'d MMM y, HH:mm' }}
                    </span>
                  }
                </div>
                <h3 class="mt-3 text-base font-medium text-[#111111]">La solicitud anterior no fue aprobada</h3>
                @if (lastRejectedRequest.decisionReason) {
                  <p class="mt-2 text-sm text-[#787774]">
                    <strong class="text-[#111111]">Motivo del administrador:</strong> {{ lastRejectedRequest.decisionReason }}
                  </p>
                }
                <p class="mt-2 text-xs text-[#787774]">
                  Puedes presentar una nueva solicitud con información más detallada.
                </p>
              </div>
            }

            <!-- Formulario de solicitud (si no hay solicitud pendiente) -->
            @if (!pendingRequest) {
              <div class="enter-rise mt-8 rounded-xl border border-[#EAEAEA] bg-white p-8">
                <h2 class="font-display text-2xl text-[#111111]">
                  {{ lastRejectedRequest ? 'Nueva solicitud' : 'Enviar solicitud' }}
                </h2>
                <p class="mt-2 text-sm text-[#787774]">
                  Describe tu experiencia, los servicios que deseas ofrecer y tus áreas de especialidad (máx. 500 caracteres).
                </p>

                <form (ngSubmit)="submitRequest()" class="mt-6 space-y-4">
                  <div>
                    <label for="message" class="block text-sm font-medium text-[#111111]">
                      Motivo o propuesta de servicios
                    </label>
                    <textarea
                      id="message"
                      rows="4"
                      maxlength="500"
                      [(ngModel)]="message"
                      name="message"
                      placeholder="Ej: Soy desarrollador frontend con 5 años de experiencia y quiero ofrecer asesorías de Angular y TypeScript..."
                      required
                      class="mt-1 block w-full rounded-md border border-[#EAEAEA] p-3 text-sm text-[#111111] placeholder-[#787774] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111] disabled:bg-[#F7F6F3]"
                      [disabled]="submitting"
                    ></textarea>
                    <div class="mt-1 flex justify-between text-xs text-[#787774]">
                      <span>El motivo es obligatorio para que el administrador verifique tu perfil.</span>
                      <span class="tabular-nums">{{ message.length }}/500</span>
                    </div>
                  </div>

                  <div class="pt-2">
                    <button
                      type="submit"
                      [disabled]="submitting || !message.trim()"
                      class="cursor-pointer rounded-md bg-[#111111] px-5 py-2.5 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:active:scale-100"
                    >
                      {{ submitting ? 'Enviando solicitud...' : 'Enviar solicitud' }}
                    </button>
                  </div>
                </form>
              </div>
            }

            <!-- Historial de solicitudes anteriores -->
            @if (requests.length > 0) {
              <div class="enter-rise mt-12">
                <h3 class="text-sm font-medium uppercase tracking-wide text-[#787774]">Historial de solicitudes</h3>
                <ul class="mt-4 divide-y divide-[#EAEAEA] rounded-xl border border-[#EAEAEA] bg-white">
                  @for (r of requests; track r.id) {
                    <li class="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between">
                      <div class="min-w-0 flex-1">
                        <div class="flex items-center gap-2">
                          <span
                            class="rounded px-2 py-0.5 text-xs font-medium"
                            [ngClass]="{
                              'bg-[#FBF3DB] text-[#795A00]': r.status === 'PENDING',
                              'bg-[#EDF3EC] text-[#346538]': r.status === 'APPROVED',
                              'bg-[#FDEBEC] text-[#9F2F2D]': r.status === 'REJECTED'
                            }"
                          >
                            {{ r.status === 'PENDING' ? 'Pendiente' : r.status === 'APPROVED' ? 'Aprobada' : 'Rechazada' }}
                          </span>
                          <span class="text-xs text-[#787774] tabular-nums">
                            {{ r.createdAt | date:'d MMM y, HH:mm' }}
                          </span>
                        </div>
                        <p class="mt-1 text-sm text-[#111111] truncate">{{ r.message }}</p>
                        @if (r.decisionReason) {
                          <p class="mt-0.5 text-xs text-[#787774]">
                            Nota del admin: {{ r.decisionReason }}
                          </p>
                        }
                      </div>
                    </li>
                  }
                </ul>
              </div>
            }

          }
        }
      </div>
    </section>
  `,
})
export class CustomerProviderRequestComponent implements OnInit {
  auth = inject(AuthService);
  private service = inject(ProviderRequestService);

  requests: ProviderRequest[] = [];
  pendingRequest: ProviderRequest | null = null;
  lastRejectedRequest: ProviderRequest | null = null;

  message = '';
  loading = false;
  submitting = false;
  error = '';
  notice = '';

  get isProviderOrAdmin(): boolean {
    return this.auth.hasRole('PROVIDER', 'ADMIN');
  }

  ngOnInit(): void {
    if (!this.isProviderOrAdmin) {
      this.load();
    }
  }

  load(): void {
    this.loading = true;
    this.service.getMyRequests().subscribe({
      next: (data) => {
        this.requests = data;
        this.pendingRequest = data.find((r) => r.status === 'PENDING') || null;
        this.lastRejectedRequest = data.find((r) => r.status === 'REJECTED') || null;
        this.loading = false;
      },
      error: (e: HttpErrorResponse) => {
        this.loading = false;
        this.error = e?.error?.detail || 'No fue posible cargar tus solicitudes.';
      },
    });
  }

  submitRequest(): void {
    const clean = this.message.trim();
    if (!clean) return;

    this.submitting = true;
    this.error = '';
    this.notice = '';

    this.service.create(clean).subscribe({
      next: (created) => {
        this.submitting = false;
        this.message = '';
        this.notice = '¡Solicitud enviada con éxito! Queda en revisión administrativa.';
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.submitting = false;
        this.error = e?.error?.detail || 'No se pudo enviar la solicitud.';
      },
    });
  }
}
