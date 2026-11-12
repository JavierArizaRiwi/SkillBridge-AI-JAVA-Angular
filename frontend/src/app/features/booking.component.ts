import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Offering, OfferingService } from '../core/offering.service';
import { BookingService } from '../core/booking.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="min-h-screen bg-slate-50 px-4 py-12 sm:px-6 lg:px-8">
      <div class="mx-auto max-w-xl">
        <div class="rounded-2xl border border-slate-200/80 bg-white p-8 shadow-sm transition-all hover:shadow-md">
          
          <!-- Encabezado -->
          <div class="mb-6">
            <span class="inline-flex items-center rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-blue-700">
              EVENT-DRIVEN FLOW
            </span>
            <h1 class="mt-3 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
              Reservar una sesión
            </h1>
            <p class="mt-2 text-sm text-slate-500">
              Al confirmar, Spring persiste la reserva y publica un evento <code class="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-blue-800">BookingCreated</code> en RabbitMQ.
            </p>
          </div>

          <!-- Alertas -->
          @if (error) {
            <div role="alert" class="mb-5 flex items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 shadow-sm">
              <svg class="h-5 w-5 shrink-0 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
              </svg>
              <span>{{ error }}</span>
            </div>
          }

          @if (success) {
            <div role="alert" class="mb-5 flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 shadow-sm">
              <svg class="h-5 w-5 shrink-0 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
              </svg>
              <span>{{ success }}</span>
            </div>
          }

          <div class="space-y-5">
            <!-- Selector de Servicio con Scroll Personalizado -->
            <div class="flex flex-col gap-1.5">
              <label class="text-xs font-semibold uppercase tracking-wider text-slate-600">
                Servicio
              </label>
              
              <div class="relative w-full" tabindex="0" (blur)="offeringDropdownOpen = false">
                <button type="button" 
                        (click)="offeringDropdownOpen = !offeringDropdownOpen"
                        class="flex w-full items-center justify-between rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-3 text-left text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100">
                  <span class="{{ selectedOfferingTitle ? 'text-slate-900 font-medium' : 'text-slate-400' }}">
                    {{ selectedOfferingTitle || 'Selecciona un servicio' }}
                  </span>
                  <svg class="h-4 w-4 text-slate-400 transition-transform duration-200" [class.rotate-180]="offeringDropdownOpen" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
                  </svg>
                </button>

                @if (offeringDropdownOpen) {
                  <div class="absolute z-20 mt-1.5 max-h-48 w-full overflow-y-auto rounded-xl border border-slate-200 bg-white p-1 shadow-xl">
                    <div class="cursor-pointer rounded-lg px-3 py-2 text-xs text-slate-400 hover:bg-slate-50"
                         (click)="selectOffering('', '')">
                      Selecciona un servicio
                    </div>
                    @for (offering of offerings; track offering.id) {
                      <div class="cursor-pointer rounded-lg px-3 py-2.5 text-sm text-slate-700 hover:bg-blue-50 hover:text-blue-900"
                           (click)="selectOffering(offering.id, offering.title)">
                        {{ offering.title }}
                      </div>
                    }
                  </div>
                }
              </div>
            </div>

            <!-- Fecha y Hora -->
            <div class="flex flex-col gap-1.5">
              <label class="text-xs font-semibold uppercase tracking-wider text-slate-600">
                Fecha y hora
              </label>
              <input type="datetime-local" [(ngModel)]="scheduledLocal"
                     class="w-full rounded-xl border border-slate-300 bg-slate-50/50 px-3.5 py-3 text-sm outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100">
            </div>

            <!-- Botón de acción -->
            <button type="button" class="w-full cursor-pointer rounded-xl bg-slate-900 px-5 py-3 text-sm font-semibold text-white shadow-sm transition-all hover:bg-blue-900 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
                    [disabled]="loading" (click)="book()">
              {{ loading ? 'Creando reserva...' : 'Crear reserva' }}
            </button>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class BookingComponent implements OnInit {
  offerings: Offering[] = [];
  offeringId = '';
  selectedOfferingTitle = '';
  scheduledLocal = '';
  loading = false;
  error = '';
  success = '';

  offeringDropdownOpen = false;

  private offeringsService = inject(OfferingService);
  private bookingService = inject(BookingService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.offeringsService.list().subscribe({
      next: (list) => {
        this.offerings = list;

        // Lee el Query Parameter 'offeringId' si viene desde Home
        const paramOfferingId = this.route.snapshot.queryParamMap.get('offeringId');
        if (paramOfferingId) {
          const matched = this.offerings.find((o) => o.id === paramOfferingId);
          if (matched) {
            this.selectOffering(matched.id, matched.title);
          }
        }
      },
      error: () => (this.error = 'No fue posible cargar los servicios.')
    });
  }

  selectOffering(id: string, title: string): void {
    this.offeringId = id;
    this.selectedOfferingTitle = title;
    this.offeringDropdownOpen = false;
  }

  book(): void {
    this.error = '';
    this.success = '';
    if (!this.offeringId || !this.scheduledLocal) {
      this.error = 'Selecciona un servicio y una fecha válida.';
      return;
    }

    this.loading = true;
    const scheduledAt = new Date(this.scheduledLocal).toISOString();

    this.bookingService.createBooking({ offeringId: this.offeringId, scheduledAt })
      .subscribe({
        next: () => {
          this.success = 'Reserva creada exitosamente.';
          this.offeringId = '';
          this.selectedOfferingTitle = '';
          this.scheduledLocal = '';
          this.loading = false;
        },
        error: (e) => {
          this.error = e?.error?.detail || 'No fue posible crear la reserva. Inicia sesión y verifica la fecha.';
          this.loading = false;
        }
      });
  }
}