import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BookingService, Booking } from '../core/booking.service';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="min-h-screen bg-slate-50 px-4 py-10 sm:px-6 lg:px-8">
      <div class="mx-auto max-w-7xl">

        <!-- Encabezado -->
        <div class="mb-10">
          <span
            class="inline-flex items-center rounded-full bg-blue-50 px-3 py-1
                   text-xs font-semibold uppercase tracking-wider text-blue-700"
          >
            Mi agenda
          </span>

          <h1 class="mt-3 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Mis reservas
          </h1>

          <p class="mt-2 max-w-2xl text-base text-slate-500">
            Consulta y revisa las sesiones que tienes agendadas.
          </p>
        </div>

        <!-- Estado de carga -->
        @if (isLoading) {
          <div
            class="flex min-h-64 flex-col items-center justify-center rounded-2xl
                   border border-slate-200 bg-white p-8 shadow-sm"
          >
            <!-- Indicador visual de carga -->
            <div
              class="mb-4 h-10 w-10 animate-spin rounded-full border-4
                     border-slate-200 border-t-blue-600"
            ></div>

            <p class="text-sm font-medium text-slate-600">
              Cargando tus reservas...
            </p>
          </div>
        }

        <!-- Estado de error -->
        @if (!isLoading && errorMessage) {
          <div
            class="rounded-2xl border border-red-200 bg-red-50 p-6 sm:p-8"
          >
            <div class="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">

              <div>
                <div class="mb-2 flex items-center gap-2">
                  <div
                    class="flex h-8 w-8 items-center justify-center rounded-full
                           bg-red-100 text-red-600"
                  >
                    !
                  </div>

                  <h2 class="font-semibold text-red-900">
                    No pudimos cargar tus reservas
                  </h2>
                </div>

                <p class="text-sm text-red-700">
                  {{ errorMessage }}
                </p>
              </div>

              <button
                type="button"
                (click)="fetchMyBookings()"
                class="inline-flex items-center justify-center rounded-xl
                       bg-red-600 px-5 py-2.5 text-sm font-semibold text-white
                       shadow-sm transition hover:bg-red-700
                       focus:outline-none focus:ring-2 focus:ring-red-500
                       focus:ring-offset-2"
              >
                Reintentar
              </button>
            </div>
          </div>
        }

        <!-- Estado vacío -->
        @if (!isLoading && !errorMessage && bookings.length === 0) {
          <div
            class="flex min-h-80 flex-col items-center justify-center rounded-2xl
                   border border-dashed border-slate-300 bg-white px-6 py-12
                   text-center shadow-sm"
          >
            <div
              class="mb-5 flex h-16 w-16 items-center justify-center
                     rounded-2xl bg-blue-50 text-blue-600"
            >
              <!-- Icono calendario -->
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-8 w-8"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                stroke-width="1.8"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M6.75 3v2.25M17.25 3v2.25M3.75 9.75h16.5M5.25 4.5h13.5a1.5 1.5 0 011.5 1.5v13.5a1.5 1.5 0 01-1.5 1.5H5.25a1.5 1.5 0 01-1.5-1.5V6a1.5 1.5 0 011.5-1.5z"
                />
              </svg>
            </div>

            <h2 class="text-lg font-semibold text-slate-900">
              No tienes reservas activas
            </h2>

            <p class="mt-2 max-w-md text-sm text-slate-500">
              Aún no has agendado ninguna sesión. Cuando realices una reserva,
              aparecerá aquí.
            </p>
          </div>
        }

        <!-- Estado exitoso -->
        @if (!isLoading && !errorMessage && bookings.length > 0) {
          <div>

            <!-- Resumen -->
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-semibold text-slate-900">
                Tus reservas
              </h2>

              <span
                class="rounded-full bg-slate-100 px-3 py-1 text-sm font-medium
                       text-slate-600"
              >
                {{ bookings.length }}
                {{ bookings.length === 1 ? 'reserva' : 'reservas' }}
              </span>
            </div>

            <!-- Grid de reservas -->
            <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">

              @for (booking of bookings; track booking.id) {
                <article
                  class="group rounded-2xl border border-slate-200 bg-white p-6
                         shadow-sm transition duration-200
                         hover:-translate-y-1 hover:border-blue-200
                         hover:shadow-lg"
                >

                  <!-- Cabecera de tarjeta -->
                  <div class="mb-6 flex items-start justify-between gap-4">

                    <div
                      class="flex h-11 w-11 shrink-0 items-center justify-center
                             rounded-xl bg-blue-50 text-blue-600"
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-5 w-5"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        stroke-width="1.8"
                      >
                        <path
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d="M6.75 3v2.25M17.25 3v2.25M3.75 9.75h16.5M5.25 4.5h13.5a1.5 1.5 0 011.5 1.5v13.5a1.5 1.5 0 01-1.5 1.5H5.25a1.5 1.5 0 01-1.5-1.5V6a1.5 1.5 0 011.5-1.5z"
                        />
                      </svg>
                    </div>

                    <!-- Estado -->
                    <span
                      class="rounded-full px-3 py-1 text-xs font-semibold"
                      [ngClass]="{
                        'bg-amber-50 text-amber-700':
                          booking.status.toLowerCase() === 'pending',

                        'bg-emerald-50 text-emerald-700':
                          booking.status.toLowerCase() === 'confirmed',

                        'bg-red-50 text-red-700':
                          booking.status.toLowerCase() === 'cancelled'
                      }"
                    >
                      {{ booking.status }}
                    </span>
                  </div>

                  <!-- Fecha -->
                  <div class="mb-5">
                    <p class="mb-1 text-xs font-semibold uppercase tracking-wider text-slate-400">
                      Fecha de la reserva
                    </p>

                    <p class="text-base font-semibold text-slate-900">
                      {{ booking.scheduledAt | date:'medium' }}
                    </p>
                  </div>
                </article>
              }

            </div>
          </div>
        }

      </div>
    </section>
  `,
})
export class MyBookingsComponent implements OnInit {
  private bookingService = inject(BookingService);

  bookings: Booking[] = [];
  isLoading = true;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.fetchMyBookings();
  }

  // Obtiene las reservas del usuario y controla los estados de carga y error.
  fetchMyBookings(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        this.bookings = data;
        this.isLoading = false;
      },

      error: (err) => {
        console.error('Error al obtener reservas:', err);
        this.errorMessage =
          'No se pudieron cargar tus reservas. Intenta nuevamente.';
        this.isLoading = false;
      },
    });
  }
}
