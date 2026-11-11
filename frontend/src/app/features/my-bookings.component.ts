import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Booking, BookingService } from '../core/booking.service';
import { Offering, OfferingService } from '../core/offering.service';
import { AuthService } from '../core/auth.service';
import { PaymentModal } from './payment-modal.component';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, PaymentModal, RouterLink],
  template: `
    <section class="min-h-screen bg-[#FBFBFA] px-4 py-16 sm:px-6 lg:py-24">
      <div class="mx-auto max-w-4xl">

        <!-- Encabezado -->
        <header class="enter-rise">
          <h1 class="font-display text-5xl text-[#111111] sm:text-6xl">Mis reservas</h1>
          <p class="mt-4 max-w-md text-base text-[#787774]">
            Consulta, paga o gestiona el estado de tus sesiones agendadas.
          </p>
        </header>

        <!-- Pestañas de filtro -->
        @if (!isLoading && !errorMessage && bookings.length > 0) {
          <div class="enter-rise mt-12 flex gap-6 overflow-x-auto border-b border-[#EAEAEA]" style="animation-delay: 60ms">
            <button (click)="statusFilter = 'ALL'"
                    [ngClass]="statusFilter === 'ALL' ? 'border-[#111111] text-[#111111]' : 'border-transparent text-[#787774] hover:text-[#111111]'"
                    class="-mb-px cursor-pointer whitespace-nowrap border-b-2 py-3 text-sm font-medium transition-colors duration-150">
              Todas <span class="ml-1 tabular-nums text-[#787774]">{{ bookings.length }}</span>
            </button>
            <button (click)="statusFilter = 'CONFIRMED'"
                    [ngClass]="statusFilter === 'CONFIRMED' ? 'border-[#111111] text-[#111111]' : 'border-transparent text-[#787774] hover:text-[#111111]'"
                    class="-mb-px cursor-pointer whitespace-nowrap border-b-2 py-3 text-sm font-medium transition-colors duration-150">
              Confirmadas <span class="ml-1 tabular-nums text-[#787774]">{{ getCountByStatus('CONFIRMED') }}</span>
            </button>
            <button (click)="statusFilter = 'CREATED'"
                    [ngClass]="statusFilter === 'CREATED' ? 'border-[#111111] text-[#111111]' : 'border-transparent text-[#787774] hover:text-[#111111]'"
                    class="-mb-px cursor-pointer whitespace-nowrap border-b-2 py-3 text-sm font-medium transition-colors duration-150">
              Pendientes <span class="ml-1 tabular-nums text-[#787774]">{{ getCountByStatus('CREATED') }}</span>
            </button>
            <button (click)="statusFilter = 'CANCELLED'"
                    [ngClass]="statusFilter === 'CANCELLED' ? 'border-[#111111] text-[#111111]' : 'border-transparent text-[#787774] hover:text-[#111111]'"
                    class="-mb-px cursor-pointer whitespace-nowrap border-b-2 py-3 text-sm font-medium transition-colors duration-150">
              Canceladas <span class="ml-1 tabular-nums text-[#787774]">{{ getCountByStatus('CANCELLED') }}</span>
            </button>
          </div>
        }

        @if (cancelSuccessMessage) {
          <div role="alert" class="enter-rise mt-8 flex items-center justify-between gap-3 rounded-lg bg-[#EDF3EC] px-4 py-3 text-sm text-[#346538]">
            <span>{{ cancelSuccessMessage }}</span>
            <button (click)="cancelSuccessMessage = ''" aria-label="Cerrar"
                    class="cursor-pointer rounded px-2 text-lg leading-none transition-[background-color,transform] duration-150 hover:bg-white/60 active:scale-[0.94]">&times;</button>
          </div>
        }

        @if (cancelErrorMessage) {
          <div role="alert" class="enter-rise mt-8 flex items-center justify-between gap-3 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">
            <span>{{ cancelErrorMessage }}</span>
            <button (click)="cancelErrorMessage = ''" aria-label="Cerrar"
                    class="cursor-pointer rounded px-2 text-lg leading-none transition-[background-color,transform] duration-150 hover:bg-white/60 active:scale-[0.94]">&times;</button>
          </div>
        }

        @if (paymentStatusMessage) {
          <div role="status" aria-live="polite" class="enter-rise mt-8 rounded-lg bg-[#FBF3DB] px-4 py-3 text-sm text-[#795A00]">
            {{ paymentStatusMessage }}
          </div>
        }

        <div class="mt-2">
          @if (isLoading) {
            <div class="flex flex-col items-center justify-center py-28">
              <div class="mb-4 h-6 w-6 animate-spin rounded-full border-2 border-[#EAEAEA] border-t-[#111111] [animation-duration:600ms]"></div>
              <p class="text-sm text-[#787774]">Cargando tus reservas...</p>
            </div>
          } @else if (errorMessage) {
            <div class="enter-rise mt-10 flex flex-col gap-5 rounded-xl border border-[#EAEAEA] bg-white p-8 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 class="font-display text-2xl text-[#111111]">Error de conexión</h2>
                <p class="mt-1 text-sm text-[#9F2F2D]">{{ errorMessage }}</p>
              </div>
              <button
                type="button"
                (click)="fetchMyBookings()"
                class="cursor-pointer rounded-md bg-[#111111] px-4 py-2.5 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] active:scale-[0.98] motion-reduce:active:scale-100"
              >
                Reintentar
              </button>
            </div>
          } @else if (bookings.length === 0) {
            <div class="enter-rise py-28 text-center">
              <h2 class="font-display text-4xl text-[#111111]">No tienes reservas activas</h2>
              <p class="mx-auto mt-4 max-w-sm text-sm leading-6 text-[#787774]">Aún no has agendado ninguna sesión. Explora los servicios disponibles para comenzar.</p>
              <a routerLink="/"
                 class="mt-8 inline-block rounded-md bg-[#111111] px-5 py-2.5 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] motion-reduce:active:scale-100">
                Explorar servicios
              </a>
            </div>
          } @else if (filteredBookings.length === 0) {
            <div class="enter-rise py-24 text-center">
              <h2 class="font-display text-3xl text-[#111111]">No hay reservas</h2>
              <p class="mx-auto mt-3 max-w-sm text-sm text-[#787774]">No se encontraron reservas con el estado seleccionado.</p>
              <button (click)="statusFilter = 'ALL'"
                      class="mt-6 cursor-pointer rounded-md px-4 py-2.5 text-sm font-medium text-[#111111] transition-[background-color,transform] duration-150 hover:bg-[#F1F0ED] active:scale-[0.97] motion-reduce:active:scale-100">
                Ver todas
              </button>
            </div>
          } @else {
            <ul>
              @for (booking of filteredBookings; track booking.id; let i = $index) {
                @let offering = getOffering(booking.offeringId);

                <li class="enter-rise flex flex-col gap-5 border-b border-[#EAEAEA] py-7 sm:flex-row sm:items-center sm:gap-8"
                    [style.animation-delay.ms]="(i < 8 ? i : 8) * 40">

                  <!-- Fecha -->
                  <div class="shrink-0 sm:w-40">
                    <p class="font-display text-2xl text-[#111111]">{{ booking.scheduledAt | date:'d MMM y' }}</p>
                    <p class="mt-0.5 text-sm text-[#787774] tabular-nums">{{ booking.scheduledAt | date:'shortTime' }}</p>
                  </div>

                  <!-- Servicio -->
                  <div class="min-w-0 flex-1">
                    <h3 class="truncate text-lg font-medium tracking-[-0.01em] text-[#111111]">
                      {{ offering?.title || 'Reserva #' + booking.id.substring(0, 8) }}
                    </h3>
                    <p class="mt-1 text-sm text-[#787774]">
                      {{ offering?.category || 'Servicio' }}
                      @if (booking.status === 'CONFIRMED') {
                        <span> · {{ canCancel(booking.scheduledAt) ? 'Reembolso del 100% hasta 24 h antes' : 'Plazo de cancelación vencido' }}</span>
                      }
                    </p>
                  </div>

                  <!-- Estado, precio y acciones -->
                  <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
                    <span
                      class="rounded-md px-2 py-0.5 text-xs font-medium"
                      [ngClass]="{
                        'bg-[#FBF3DB] text-[#956400]': booking.status === 'CREATED',
                        'bg-[#EDF3EC] text-[#346538]': booking.status === 'CONFIRMED' || booking.status === 'COMPLETED',
                        'bg-[#FDEBEC] text-[#9F2F2D]': booking.status === 'CANCELLED'
                      }"
                    >
                      {{
                        booking.status === 'CREATED' ? 'Pendiente de pago' :
                        booking.status === 'CONFIRMED' ? 'Confirmada' :
                        booking.status === 'COMPLETED' ? 'Completada' :
                        booking.status === 'CANCELLED' ? 'Cancelada' : booking.status
                      }}
                    </span>

                    <span class="w-24 text-sm font-medium text-[#111111] tabular-nums sm:text-right">
                      {{ getOfferingPrice(booking.offeringId) | currency: "COP" : "symbol-narrow" : "1.0-0" }}
                    </span>

                    <div class="flex min-w-[8.5rem] justify-end">
                      @if (booking.status === 'CREATED') {
                        <button
                          (click)="openPaymentModal(booking)"
                          class="cursor-pointer rounded-md bg-[#111111] px-4 py-2 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] motion-reduce:active:scale-100"
                        >
                          Pagar reserva
                        </button>
                      }

                      @if (booking.status === 'CONFIRMED') {
                        @if (canCancel(booking.scheduledAt)) {
                          <button
                            (click)="openCancelModal(booking)"
                            [disabled]="isCancelingId === booking.id"
                            class="cursor-pointer rounded-md px-3 py-2 text-sm font-medium text-[#9F2F2D] transition-[background-color,transform,opacity] duration-150 hover:bg-[#FDEBEC] active:scale-[0.97] disabled:pointer-events-none disabled:opacity-50 motion-reduce:active:scale-100"
                          >
                            {{ isCancelingId === booking.id ? 'Cancelando...' : 'Cancelar reserva' }}
                          </button>
                        } @else {
                          <span class="cursor-not-allowed px-3 py-2 text-sm font-medium text-[#B8B7B3]" title="No se puede cancelar faltando menos de 24 horas">
                            Cancelar reserva
                          </span>
                        }
                      }
                    </div>
                  </div>
                </li>
              }
            </ul>
          }
        </div>
      </div>
    </section>

    <!-- Modal de confirmación para cancelar -->
    @if (bookingToCancel) {
      <div class="enter-fade fixed inset-0 z-50 flex items-center justify-center bg-[#111111]/25 p-4">
        <div class="enter-modal w-full max-w-md rounded-xl border border-[#EAEAEA] bg-white p-7 shadow-[0_8px_40px_rgba(0,0,0,0.08)]">
          <h3 class="font-display text-3xl text-[#111111]">¿Cancelar esta reserva?</h3>
          <p class="mt-3 text-sm leading-6 text-[#2F3437]">
            Esta acción cancelará tu sesión agendada y procesará la solicitud de reembolso. ¿Estás seguro de continuar?
          </p>

          <div class="mt-8 flex justify-end gap-2">
            <button
              type="button"
              (click)="bookingToCancel = null"
              class="cursor-pointer rounded-md px-4 py-2.5 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100"
            >
              Volver
            </button>
            <button
              type="button"
              (click)="confirmCancelBooking()"
              class="cursor-pointer rounded-md bg-[#9F2F2D] px-4 py-2.5 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#862624] active:scale-[0.98] motion-reduce:active:scale-100"
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
export class MyBookingsComponent implements OnInit, OnDestroy {
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
  paymentStatusMessage = '';
  private paymentPollingTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.fetchMyBookings();
    this.handleStripeReturn();
    this.offeringService.list().subscribe({
      next: (data) => (this.offerings = data),
      error: () => console.warn('No se pudieron cargar las especificaciones de servicios.')
    });
  }

  ngOnDestroy(): void {
    if (this.paymentPollingTimer) {
      clearTimeout(this.paymentPollingTimer);
    }
  }

  private handleStripeReturn(): void {
    const params = new URLSearchParams(window.location.search);
    const bookingId = params.get('payment_booking_id');
    const paymentIntent = params.get('payment_intent');
    if (!bookingId || !paymentIntent) return;

    // El client secret de Stripe puede venir en la URL de retorno; quitar los
    // parámetros de pago del historial del navegador tan pronto como se lean.
    window.history.replaceState({}, document.title, window.location.pathname + window.location.hash);

    const redirectStatus = params.get('redirect_status');
    if (redirectStatus === 'failed') {
      this.paymentStatusMessage = 'Stripe no pudo completar el pago. Puedes intentarlo nuevamente.';
      return;
    }

    this.paymentStatusMessage = 'Pago autorizado. Estamos confirmando tu reserva...';
    this.pollBookingStatus(bookingId, 20);
  }

  private pollBookingStatus(bookingId: string, attemptsRemaining: number): void {
    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        this.bookings = data;
        const booking = data.find((item) => item.id === bookingId);
        if (booking?.status === 'CONFIRMED' || booking?.status === 'COMPLETED') {
          this.paymentStatusMessage = 'Pago confirmado y reserva actualizada.';
          return;
        }

        if (attemptsRemaining <= 1) {
          this.paymentStatusMessage = 'El pago fue autorizado y Stripe aún está notificando la confirmación. Esta página seguirá mostrando el estado actualizado al volver a cargar.';
          return;
        }

        this.paymentPollingTimer = setTimeout(() => this.pollBookingStatus(bookingId, attemptsRemaining - 1), 1500);
      },
      error: () => {
        if (attemptsRemaining > 1) {
          this.paymentPollingTimer = setTimeout(() => this.pollBookingStatus(bookingId, attemptsRemaining - 1), 1500);
        } else {
          this.paymentStatusMessage = 'No pudimos consultar el estado todavía. Recarga la página en unos segundos.';
        }
      }
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
