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
    <section class="min-h-screen bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto grid max-w-5xl gap-12 lg:grid-cols-[1fr_26rem] lg:gap-20">

        <!-- Columna editorial -->
        <div class="enter-rise">
          <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">Reservar una sesión</h1>
          <p class="mt-6 max-w-md text-base leading-7 text-[#2F3437]">
            Al confirmar, Spring persiste la reserva y publica un evento <code class="rounded bg-[#F1F0ED] px-1.5 py-0.5 font-mono text-sm text-[#111111]">BookingCreated</code> en RabbitMQ.
          </p>
        </div>

        <!-- Formulario -->
        <div class="enter-rise self-start rounded-xl border border-[#EAEAEA] bg-white p-6 sm:p-8" style="animation-delay: 70ms">

          @if (error) {
            <div role="alert" class="enter-rise mb-6 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">{{ error }}</div>
          }

          @if (success) {
            <div role="alert" class="enter-rise mb-6 rounded-lg bg-[#EDF3EC] px-4 py-3 text-sm text-[#346538]">{{ success }}</div>
          }

          <div class="space-y-6">
            <!-- Servicio -->
            <div>
              <label class="mb-2 block text-sm text-[#787774]">Servicio</label>

              <div class="relative w-full" tabindex="0" (blur)="offeringDropdownOpen = false">
                <button type="button" [disabled]="offeringsLoading"
                        (click)="offeringDropdownOpen = !offeringDropdownOpen"
                        class="flex w-full cursor-pointer items-center justify-between rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-left text-sm outline-none transition-[border-color,transform] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:border-[#C9C8C4] focus:border-[#111111] active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:active:scale-100">
                  <span class="inline-flex items-center gap-2 {{ selectedOfferingTitle ? 'text-[#111111] font-medium' : 'text-[#9B9A97]' }}">
                    @if (offeringsLoading) {
                      <svg class="h-3.5 w-3.5 animate-spin text-[#787774] [animation-duration:600ms]" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                      </svg>
                      Cargando servicios...
                    } @else {
                      {{ selectedOfferingTitle || 'Selecciona un servicio' }}
                    }
                  </span>
                  <svg class="h-3.5 w-3.5 text-[#787774] transition-transform duration-200 ease-[cubic-bezier(0.23,1,0.32,1)]" [class.rotate-180]="offeringDropdownOpen" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                    <path d="M213.66 101.66l-80 80a8 8 0 0 1-11.32 0l-80-80a8 8 0 0 1 11.32-11.32L128 164.69l74.34-74.35a8 8 0 0 1 11.32 11.32Z"/>
                  </svg>
                </button>

                @if (offeringDropdownOpen) {
                  <div class="enter-drop absolute z-20 mt-2 max-h-56 w-full overflow-y-auto rounded-lg border border-[#EAEAEA] bg-white p-1 shadow-[0_8px_30px_rgba(0,0,0,0.06)]">
                    <div class="cursor-pointer rounded-md px-3 py-2 text-sm text-[#787774] transition-[background-color,transform] duration-150 hover:bg-[#F1F0ED] active:scale-[0.99] motion-reduce:active:scale-100"
                         (click)="selectOffering('', '')">
                      Selecciona un servicio
                    </div>
                    @for (offering of offerings; track offering.id) {
                      <div class="cursor-pointer rounded-md px-3 py-2.5 text-sm font-medium text-[#111111] transition-[background-color,transform] duration-150 hover:bg-[#F1F0ED] active:scale-[0.99] motion-reduce:active:scale-100"
                           (click)="selectOffering(offering.id, offering.title)">
                        {{ offering.title }}
                      </div>
                    }
                  </div>
                }
              </div>
            </div>

            <!-- Fecha y hora -->
            <div>
              <label class="mb-2 block text-sm text-[#787774]">Fecha y hora</label>
              <input type="datetime-local" [(ngModel)]="scheduledLocal"
                     class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 hover:border-[#C9C8C4] focus:border-[#111111]">
            </div>

            <button type="button" (click)="book()"
                    class="w-full cursor-pointer rounded-md bg-[#111111] px-4 py-3 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111]"
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
    this.offeringsService.list().subscribe({
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