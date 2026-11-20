import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Booking, BookingService } from '../core/booking.service';
import { Offering, OfferingService } from '../core/offering.service';
import { AuthService } from '../core/auth.service';
import { PaymentModal } from './payment-modal/payment-modal';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, PaymentModal, RouterLink],
  template: `
    <section class="min-h-screen bg-slate-50 px-4 py-10 sm:px-6 lg:px-8">
      <div class="mx-auto max-w-7xl">
        <!-- Encabezado -->
        <div class="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <span class="inline-flex items-center rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-blue-700">
              Mi agenda
            </span>
            <h1 class="mt-2 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
              Mis reservas
            </h1>
            <p class="mt-1 text-sm text-slate-500">
              Consulta, paga o gestiona el estado de tus sesiones agendadas.
            </p>
          </div>

          @if (!isLoading && !errorMessage && bookings.length > 0) {
            
<div class="flex flex-wrap items-center gap-3">
  <button (click)="statusFilter = 'ALL'" [class.opacity-50]="statusFilter !== 'ALL'" class="cursor-pointer rounded-xl border border-slate-200 bg-white px-4 py-2 text-center shadow-sm transition hover:opacity-100">
    <span class="block text-xs font-medium text-slate-400">Total</span>
    <span class="text-base font-bold text-slate-900">{{ bookings.length }}</span>
  </button>
  <button (click)="statusFilter = 'CONFIRMED'" [class.opacity-50]="statusFilter !== 'CONFIRMED'" class="cursor-pointer rounded-xl border border-emerald-100 bg-emerald-50/50 px-4 py-2 text-center shadow-sm transition hover:opacity-100">
    <span class="block text-xs font-medium text-emerald-600">Confirmadas</span>
    <span class="text-base font-bold text-emerald-700">{{ getCountByStatus('CONFIRMED') }}</span>
  </button>
  <button (click)="statusFilter = 'CREATED'" [class.opacity-50]="statusFilter !== 'CREATED'" class="cursor-pointer rounded-xl border border-amber-100 bg-amber-50/50 px-4 py-2 text-center shadow-sm transition hover:opacity-100">
    <span class="block text-xs font-medium text-amber-600">Pendientes</span>
    <span class="text-base font-bold text-amber-700">{{ getCountByStatus('CREATED') }}</span>
  </button>
  <button (click)="statusFilter = 'CANCELLED'" [class.opacity-50]="statusFilter !== 'CANCELLED'" class="cursor-pointer rounded-xl border border-red-100 bg-red-50/50 px-4 py-2 text-center shadow-sm transition hover:opacity-100">
    <span class="block text-xs font-medium text-red-600">Canceladas</span>
    <span class="text-base font-bold text-red-700">{{ getCountByStatus('CANCELLED') }}</span>
  </button>
</div>

          }
        </div>

        @if (cancelSuccessMessage) {
          <div role="alert" class="mb-6 flex items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 shadow-sm">
            <div class="flex items-center gap-2">
              <svg class="h-5 w-5 shrink-0 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
              </svg>
              <span>{{ cancelSuccessMessage }}</span>
            </div>
            <button (click)="cancelSuccessMessage = ''" class="text-emerald-500 hover:text-emerald-800">&times;</button>
          </div>
        }

        @if (cancelErrorMessage) {
          <div role="alert" class="mb-6 flex items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 shadow-sm">
            <div class="flex items-center gap-2">
              <svg class="h-5 w-5 shrink-0 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span>{{ cancelErrorMessage }}</span>
            </div>
            <button (click)="cancelErrorMessage = ''" class="text-red-500 hover:text-red-800">&times;</button>
          </div>
        }

        @if (isLoading) {
          <div class="flex min-h-64 flex-col items-center justify-center rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
            <div class="mb-4 h-10 w-10 animate-spin rounded-full border-4 border-slate-200 border-t-blue-600"></div>
            <p class="text-sm font-medium text-slate-600">Cargando tus reservas...</p>
          </div>
        } @else if (errorMessage) {
          <div class="rounded-2xl border border-red-200 bg-red-50 p-6 sm:p-8">
            <div class="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div class="mb-1 flex items-center gap-2">
                  <span class="flex h-6 w-6 items-center justify-center rounded-full bg-red-100 text-xs font-bold text-red-600">!</span>
                  <h2 class="font-semibold text-red-900">Error de conexión</h2>
                </div>
                <p class="text-sm text-red-700">{{ errorMessage }}</p>
              </div>
              <button
                type="button"
                (click)="fetchMyBookings()"
                class="inline-flex cursor-pointer items-center justify-center rounded-xl bg-red-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-red-700"
              >
                Reintentar
              </button>
            </div>
          </div>
        } @else if (bookings.length === 0) {
          <div class="flex min-h-80 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center shadow-sm">
            <div class="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50 text-blue-600">
              <svg class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3.75 9.75h16.5M5.25 4.5h13.5a1.5 1.5 0 011.5 1.5v13.5a1.5 1.5 0 01-1.5 1.5H5.25a1.5 1.5 0 01-1.5-1.5V6a1.5 1.5 0 011.5-1.5z" />
              </svg>
            </div>
            <h2 class="text-lg font-semibold text-slate-900">No tienes reservas activas</h2>
            <p class="mt-1 max-w-md text-sm text-slate-500">Aún no has agendado ninguna sesión. Explora los servicios disponibles para comenzar.</p>
            <a routerLink="/" class="mt-6 inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-900">
              Explorar servicios
            </a>
          </div>
        } @else if (filteredBookings.length === 0) {
  <div class="flex min-h-64 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center shadow-sm">
    <h2 class="text-lg font-semibold text-slate-900">No hay reservas</h2>
    <p class="mt-1 max-w-md text-sm text-slate-500">No se encontraron reservas con el estado seleccionado.</p>
    <button (click)="statusFilter = 'ALL'" class="mt-6 inline-flex cursor-pointer items-center gap-2 rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50">
      Ver todas
    </button>
  </div>
} @else {
  <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
    @for (booking of filteredBookings; track booking.id) {
              @let offering = getOffering(booking.offeringId);

              <article class="group flex flex-col justify-between rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition duration-300 hover:-translate-y-1 hover:border-blue-200 hover:shadow-xl">
                <div>
                  <div class="mb-4 flex items-center justify-between gap-2">
                    <span class="rounded-lg bg-blue-50 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider text-blue-800">
                      {{ offering?.category || 'Servicio' }}
                    </span>

                    <span
                      class="rounded-full px-3 py-1 text-xs font-semibold"
                      [ngClass]="{
                        'bg-amber-50 text-amber-700 border border-amber-200/60': booking.status === 'CREATED',
                        'bg-emerald-50 text-emerald-700 border border-emerald-200/60': booking.status === 'CONFIRMED' || booking.status === 'COMPLETED',
                        'bg-red-50 text-red-700 border border-red-200/60': booking.status === 'CANCELLED'
                      }"
                    >
                      {{
                        booking.status === 'CREATED' ? 'Pendiente de pago' :
                        booking.status === 'CONFIRMED' ? 'Confirmada' :
                        booking.status === 'COMPLETED' ? 'Completada' :
                        booking.status === 'CANCELLED' ? 'Cancelada' : booking.status
                      }}
                    </span>
                  </div>

                  <h3 class="text-lg font-bold text-slate-900 transition group-hover:text-blue-900">
                    {{ offering?.title || 'Reserva #' + booking.id.substring(0, 8) }}
                  </h3>

                  <div class="my-4 rounded-xl border border-slate-100 bg-slate-50/60 p-3.5">
                    <div class="flex items-center gap-3">
                      <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-blue-600 shadow-xs">
                        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                      </div>
                      <div>
                        <p class="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Fecha agendada</p>
                        <p class="text-xs font-bold text-slate-800">
                          {{ booking.scheduledAt | date:'medium' }}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="mt-4 border-t border-slate-100 pt-4">
                  <div class="mb-4 flex items-baseline justify-between">
                    <span class="text-xs font-medium text-slate-400">Monto total</span>
                    <strong class="text-lg font-bold text-slate-900">
                      {{ getOfferingPrice(booking.offeringId) | currency: "COP" : "symbol-narrow" : "1.0-0" }}
                    </strong>
                  </div>

                  <div class="space-y-2">
                    @if (booking.status === 'CREATED') {
                      <button
                        (click)="openPaymentModal(booking)"
                        class="w-full cursor-pointer rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-900 active:scale-[0.99]"
                      >
                        Pagar reserva
                      </button>
                    }

                    @if (booking.status === 'CONFIRMED') {
                      @if (canCancel(booking.scheduledAt)) {
                        <button
                          (click)="openCancelModal(booking)"
                          [disabled]="isCancelingId === booking.id"
                          class="w-full cursor-pointer rounded-xl border border-red-200 bg-white px-4 py-2 text-sm font-semibold text-red-600 transition hover:bg-red-50 hover:border-red-300 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {{ isCancelingId === booking.id ? 'Cancelando...' : 'Cancelar reserva' }}
                        </button>
                        <p class="text-[10px] text-center text-slate-400">Reembolso del 100% aplicable</p>
                      } @else {
                        <div class="w-full cursor-not-allowed rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-center text-sm font-semibold text-slate-400" title="No se puede cancelar faltando menos de 24 horas">
                          Cancelar reserva
                        </div>
                        <p class="text-[10px] text-center text-slate-400">Expiró el plazo límite (24h de anticipación)</p>
                      }
                    }
                  </div>
                </div>
              </article>
            }
          </div>
        }
      </div>
    </section>

    <!-- Modal de confirmación para cancelar reserva -->
    @if (bookingToCancel) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-xs">
        <div class="w-full max-w-md overflow-hidden rounded-2xl bg-white p-6 shadow-2xl transition-all">
          <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
            <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>

          <div class="mt-4 text-center">
            <h3 class="text-lg font-bold text-slate-900">¿Cancelar esta reserva?</h3>
            <p class="mt-2 text-sm text-slate-500">
              Esta acción cancelará tu sesión agendada y procesará la solicitud de reembolso. ¿Estás seguro de continuar?
            </p>
          </div>

          <div class="mt-6 flex gap-3">
            <button
              type="button"
              (click)="bookingToCancel = null"
              class="w-full cursor-pointer rounded-xl border border-slate-200 bg-white py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
            >
              Volver
            </button>
            <button
              type="button"
              (click)="confirmCancelBooking()"
              class="w-full cursor-pointer rounded-xl bg-red-600 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-red-700"
            >
              Sí, cancelar
            </button>
          </div>
        </div>
      </div>
    }

    @if (selectedBookingForPayment) {
      <app-payment-modal
        [bookingId]="selectedBookingForPayment.id"
        [amount]="getOfferingPrice(selectedBookingForPayment.offeringId)"
        (close)="closePaymentModal()"
        (paymentSuccess)="onPaymentSuccess()">
      </app-payment-modal>
    }
  `
})
export class MyBookingsComponent implements OnInit {
  statusFilter: string = 'ALL';
  
  get filteredBookings(): Booking[] {
    if (this.statusFilter === 'ALL') {
      return this.bookings;
    }
    return this.bookings.filter(b => b.status === this.statusFilter);
  }

  private bookingService = inject(BookingService);
  private authService = inject(AuthService);
  private offeringService = inject(OfferingService);

  bookings: Booking[] = [];
  offerings: Offering[] = [];
  isLoading = true;
  errorMessage: string | null = null;

  selectedBookingForPayment: Booking | null = null;
  bookingToCancel: Booking | null = null;

  isCancelingId: string | null = null;
  cancelSuccessMessage = '';
  cancelErrorMessage = '';

  ngOnInit(): void {
    this.fetchMyBookings();
    this.offeringService.list().subscribe({
      next: (data) => (this.offerings = data),
      error: () => console.warn('No se pudieron cargar las especificaciones de servicios.')
    });
  }

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
        this.errorMessage = 'No se pudieron cargar tus reservas. Intenta nuevamente.';
        this.isLoading = false;
      },
    });
  }

  getOffering(offeringId: string): Offering | undefined {
    return this.offerings.find((o) => o.id === offeringId);
  }

  getOfferingPrice(offeringId: string): number {
    const offering = this.getOffering(offeringId);
    return offering ? offering.price : 50000;
  }

  canCancel(scheduledAt: string): boolean {
    const bookingDate = new Date(scheduledAt);
    const now = new Date();
    const diffHours = (bookingDate.getTime() - now.getTime()) / (1000 * 60 * 60);
    return diffHours > 24;
  }

  openCancelModal(booking: Booking): void {
    this.bookingToCancel = booking;
  }

  confirmCancelBooking(): void {
    if (!this.bookingToCancel) return;

    const bookingId = this.bookingToCancel.id;
    this.bookingToCancel = null; // Cierra el modal de confirmación

    this.isCancelingId = bookingId;
    this.cancelSuccessMessage = '';
    this.cancelErrorMessage = '';

    this.bookingService.cancelBooking(bookingId).subscribe({
      next: (updatedBooking) => {
        this.isCancelingId = null;
        this.cancelSuccessMessage = 'Reserva cancelada exitosamente. Se ha procesado el reembolso a tu método de pago.';
        const index = this.bookings.findIndex((b) => b.id === bookingId);
        if (index !== -1) {
          this.bookings[index] = updatedBooking;
        }
      },
      error: (err) => {
        this.isCancelingId = null;
        this.cancelErrorMessage = 'No se pudo cancelar la reserva. Es posible que haya expirado el tiempo permitido.';
        console.error(err);
      },
    });
  }

  getCountByStatus(status: string): number {
    return this.bookings.filter((b) => b.status === status).length;
  }

  openPaymentModal(booking: Booking): void {
    this.selectedBookingForPayment = booking;
  }

  closePaymentModal(): void {
    this.selectedBookingForPayment = null;
  }

  onPaymentSuccess(): void {
    if (this.selectedBookingForPayment) {
      this.selectedBookingForPayment.status = 'CONFIRMED';
    }
    this.selectedBookingForPayment = null;
  }
}