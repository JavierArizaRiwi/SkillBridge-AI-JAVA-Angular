import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Offering, OfferingService } from '../core/offering.service';
import { BookingService } from '../core/booking.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="min-h-[calc(100vh-4rem)] bg-[#FBFBFA] px-4 py-12 sm:px-6 lg:py-16">
      <div class="mx-auto max-w-xl">

        <!-- Encabezado de la página -->
        <div class="enter-rise mb-8 text-center">
          <h1 class="font-display text-4xl text-[#111111] sm:text-5xl">Reservar una sesión</h1>
          <p class="mt-3 text-sm text-[#787774]">
            Selecciona el servicio que deseas y agenda la fecha y hora que mejor se adapte a tu horario.
          </p>
        </div>

        <!-- Tarjeta del Formulario -->
        <div class="enter-rise rounded-2xl border border-[#EAEAEA] bg-white p-6 shadow-sm sm:p-10" style="animation-delay: 70ms">

          @if (error) {
            <div role="alert" class="enter-rise mb-6 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">
              {{ error }}
            </div>
          }

          @if (success) {
            <div role="alert" class="enter-rise mb-6 rounded-lg bg-[#EDF3EC] px-4 py-3 text-sm text-[#346538]">
              {{ success }}
            </div>
          }

          <div class="space-y-6">
            <!-- Selector de Servicio -->
            <div>
              <label class="mb-2 block text-xs font-semibold uppercase tracking-wider text-[#787774]">
                Servicio
              </label>

              <div class="relative w-full" tabindex="0" (blur)="offeringDropdownOpen = false">
                <button type="button" [disabled]="offeringsLoading"
                        (click)="offeringDropdownOpen = !offeringDropdownOpen"
                        class="flex w-full cursor-pointer items-center justify-between rounded-lg border border-[#EAEAEA] bg-white px-4 py-3 text-left text-sm outline-none transition-[border-color,transform] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:border-[#C9C8C4] focus:border-[#111111] active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50">
                  <span class="inline-flex items-center gap-2 {{ selectedOfferingTitle ? 'text-[#111111] font-medium' : 'text-[#9B9A97]' }}">
                    @if (offeringsLoading) {
                      <svg class="h-4 w-4 animate-spin text-[#787774]" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                      </svg>
                      Cargando servicios...
                    } @else {
                      {{ selectedOfferingTitle || 'Selecciona un servicio' }}
                    }
                  </span>
                  <svg class="h-4 w-4 text-[#787774] transition-transform duration-200 ease-[cubic-bezier(0.23,1,0.32,1)]"
                       [class.rotate-180]="offeringDropdownOpen" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                    <path d="M213.66 101.66l-80 80a8 8 0 0 1-11.32 0l-80-80a8 8 0 0 1 11.32-11.32L128 164.69l74.34-74.35a8 8 0 0 1 11.32 11.32Z"/>
                  </svg>
                </button>

                @if (offeringDropdownOpen) {
                  <div class="enter-drop absolute z-20 mt-2 max-h-56 w-full overflow-y-auto rounded-lg border border-[#EAEAEA] bg-white p-1.5 shadow-[0_10px_30px_rgba(0,0,0,0.08)]">
                    <div class="cursor-pointer rounded-md px-3 py-2 text-sm text-[#787774] transition-colors duration-150 hover:bg-[#F1F0ED]"
                         (click)="selectOffering('', '')">
                      Selecciona un servicio
                    </div>
                    @for (offering of offerings; track offering.id) {
                      <div class="cursor-pointer rounded-md px-3 py-2.5 text-sm font-medium text-[#111111] transition-colors duration-150 hover:bg-[#F1F0ED]"
                           (click)="selectOffering(offering.id, offering.title)">
                        {{ offering.title }}
                      </div>
                    }
                  </div>
                }
              </div>
            </div>

            <!-- Selector de Fecha y Hora -->
            <div>
              <label class="mb-2 block text-xs font-semibold uppercase tracking-wider text-[#787774]">
                Fecha y hora
              </label>
              <input type="datetime-local" [(ngModel)]="scheduledLocal"
                     class="w-full rounded-lg border border-[#EAEAEA] bg-white px-4 py-3 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]">
            </div>

            <!-- Botón de Envío -->
            <button type="button" (click)="book()"
                    class="mt-2 w-full cursor-pointer rounded-lg bg-[#111111] px-4 py-3.5 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40"
                    [disabled]="loading">
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
  offeringsLoading = true;
  error = '';
  success = '';

  offeringDropdownOpen = false;

  private offeringsService = inject(OfferingService);
  private bookingService = inject(BookingService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  ngOnInit(): void {
    this.offeringsService.listForLookup().subscribe({
      next: (list) => {
        this.offerings = list;
        this.offeringsLoading = false;

        const paramOfferingId = this.route.snapshot.queryParamMap.get('offeringId');
        if (paramOfferingId) {
          const matched = this.offerings.find((o) => o.id === paramOfferingId);
          if (matched) {
            this.selectOffering(matched.id, matched.title);
          }
        }
      },
      error: () => {
        this.error = 'No fue posible cargar los servicios.';
        this.offeringsLoading = false;
      }
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
          this.router.navigate(['/bookings/me']);
        },
        error: (e: HttpErrorResponse) => {
          this.error = e?.error?.detail || 'No fue posible crear la reserva. Inicia sesión y verifica la fecha.';
          this.loading = false;
        }
      });
  }
}
