import { Component, EventEmitter, Input, OnInit, Output, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../core/api';
import { StripeService } from '../core/stripe.service';
import { Stripe, StripeElements, StripePaymentElement } from '@stripe/stripe-js';

@Component({
  selector: 'app-payment-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Scrim -->
    <div class="enter-fade fixed inset-0 z-50 flex items-center justify-center bg-[#111111]/30 p-4 backdrop-blur-[2px]">
      <!-- Panel: modal centrado con scroll interno y límites responsivos -->
      <div class="enter-modal relative flex max-h-[90vh] w-full max-w-lg flex-col rounded-xl border border-[#EAEAEA] bg-white text-left shadow-[0_12px_48px_rgba(0,0,0,0.12)]">

        <!-- Encabezado (Sticky arriba) -->
        <div class="flex items-start justify-between border-b border-[#F1F0ED] px-6 py-5">
          <div>
            <h3 class="font-display text-2xl font-semibold text-[#111111]">Pagar reserva</h3>
            @if (stripeAvailable) {
              <div class="mt-1 flex items-center gap-1.5 text-xs text-[#787774]">
                <span class="inline-block h-2 w-2 rounded-full bg-emerald-500"></span>
                <span>Pago seguro procesado por Stripe</span>
              </div>
            }
          </div>
          <button
            type="button"
            (click)="close.emit()"
            aria-label="Cerrar"
            class="-mr-2 -mt-1 cursor-pointer rounded-md p-2 text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.94] motion-reduce:active:scale-100"
          >
            <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
              <path d="M205.66 194.34a8 8 0 0 1-11.32 11.32L128 139.31l-66.34 66.35a8 8 0 0 1-11.32-11.32L116.69 128 50.34 61.66a8 8 0 0 1 11.32-11.32L128 116.69l66.34-66.35a8 8 0 0 1 11.32 11.32L139.31 128Z"/>
            </svg>
          </button>
        </div>

        <!-- Formulario completo que envuelve el cuerpo y los botones -->
        <form (ngSubmit)="submitPayment()" #paymentForm="ngForm" class="flex flex-1 flex-col overflow-hidden">
          
          <!-- Cuerpo con Scroll Seguro -->
          <div class="flex-1 overflow-y-auto px-6 py-5">
            <p class="text-xs leading-5 text-[#787774]">
              <span class="font-medium text-[#111111]">Política de cancelación.</span> Puedes cancelar la reserva y obtener un reembolso completo hasta 24 horas antes de iniciar la sesión.
            </p>

            <!-- Alerta de Error -->
            @if (errorMsg) {
              <div role="alert" class="enter-rise mt-4 rounded-lg bg-[#FDEBEC] px-4 py-3">
                <p class="text-xs font-medium text-[#9F2F2D]">{{ errorMsg }}</p>
              </div>
            }

            <!-- Indicador de Inicialización de Stripe -->
            @if (initializingStripe) {
              <div class="my-8 flex flex-col items-center justify-center space-y-3 py-6">
                <div class="h-6 w-6 animate-spin rounded-full border-2 border-[#111111] border-t-transparent"></div>
                <p class="text-xs text-[#787774]">Cargando pasarela de pago segura...</p>
              </div>
            }

            @if (!initializingStripe) {
              <div class="mt-4 space-y-4">

                <!-- Monto (solo lectura) -->
                <div>
                  <label class="mb-1.5 block text-xs font-medium text-[#787774]">Monto a pagar</label>
                  <input
                    type="text"
                    disabled
                    [value]="'$' + (amount | number)"
                    class="block w-full cursor-not-allowed rounded-md border border-[#EAEAEA] bg-[#F7F6F3] px-3 py-2 text-sm font-semibold text-[#111111] tabular-nums"
                  >
                </div>

                <!-- Guía de pruebas disponible en el entorno académico -->
                @if (stripeAvailable) {
                  <details class="group overflow-hidden rounded-lg border border-[#EAEAEA] bg-[#FAFAF9]">
                    <summary class="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-medium text-[#111111] marker:content-none">
                      <span class="flex items-center gap-2">
                        <svg class="h-4 w-4 text-[#787774]" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                          <path d="M224 72H32A16 16 0 0 0 16 88v80a16 16 0 0 0 16 16h192a16 16 0 0 0 16-16V88a16 16 0 0 0-16-16Zm0 96H32V88h192v80Zm-24-40a8 8 0 0 1-8 8h-48a8 8 0 0 1 0-16h48a8 8 0 0 1 8 8Zm-80 0a8 8 0 0 1-8 8H64a8 8 0 0 1 0-16h48a8 8 0 0 1 8 8Z"/>
                        </svg>
                        Tarjetas de prueba
                      </span>
                      <svg class="h-4 w-4 text-[#787774] transition-transform duration-150 group-open:rotate-180" viewBox="0 0 256 256" fill="none" stroke="currentColor" stroke-width="20" aria-hidden="true">
                        <path d="m48 96 80 80 80-80"/>
                      </svg>
                    </summary>

                    <div class="border-t border-[#EAEAEA] px-4 py-3">
                      <p class="mb-3 text-xs leading-5 text-[#787774]">
                        Solo para pruebas. Usa una fecha futura y cualquier CVC de tres dígitos.
                      </p>
                      <ul class="space-y-2" aria-label="Casos de prueba de Stripe">
                        @for (card of stripeTestCards; track card.number) {
                          <li class="rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5">
                            <div class="flex items-start justify-between gap-3">
                              <span class="font-mono text-xs font-semibold tracking-wide text-[#111111]">{{ card.number }}</span>
                              <span
                                class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium"
                                [ngClass]="{
                                  'bg-[#EAF3E9] text-[#346538]': card.tone === 'success',
                                  'bg-[#FDEBEC] text-[#9F2F2D]': card.tone === 'error',
                                  'bg-[#FFF4D9] text-[#8A5A00]': card.tone === 'warning'
                                }"
                              >
                                {{ card.outcome }}
                              </span>
                            </div>
                            <p class="mt-1 text-xs leading-4 text-[#787774]">{{ card.description }}</p>
                          </li>
                        }
                      </ul>
                    </div>
                  </details>
                }

                <!-- Contenedor Oficial de Stripe Elements -->
                @if (stripeAvailable) {
                  <div>
                    <label class="mb-1.5 block text-xs font-medium text-[#787774]">Información de la tarjeta</label>
                    <div #paymentElementContainer id="payment-element" class="rounded-md border border-[#EAEAEA] p-3 bg-white"></div>
                  </div>
                }

              </div>
            }
          </div>

          <!-- Pie del Modal (Sticky abajo, siempre visible en pantalla) -->
          <div class="flex items-center justify-end gap-2 border-t border-[#F1F0ED] bg-[#FAFAF9] px-6 py-4">
            <button
              type="button"
              (click)="close.emit()"
              class="cursor-pointer rounded-md px-4 py-2 text-sm font-medium text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.97] motion-reduce:active:scale-100"
            >
              Cancelar
            </button>
            <button
              type="submit"
              [disabled]="loading || initializingStripe || !stripeAvailable"
              class="cursor-pointer rounded-md bg-[#111111] px-5 py-2 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40 motion-reduce:active:scale-100"
            >
              {{ loading ? 'Procesando...' : 'Confirmar pago' }}
            </button>
          </div>

        </form>

      </div>
    </div>
  `
})
export class PaymentModal implements OnInit, OnDestroy {
  @Input() bookingId!: string;
  @Input() amount: number = 0;
  
  @Output() close = new EventEmitter<void>();
  @Output() paymentSuccess = new EventEmitter<void>();

  @ViewChild('paymentElementContainer') paymentElementRef?: ElementRef<HTMLDivElement>;

  // Variables de control de Stripe
  stripe: Stripe | null = null;
  elements: StripeElements | null = null;
  paymentElement: StripePaymentElement | null = null;
  clientSecret: string = '';

  // Estado del componente
  initializingStripe = true;
  stripeAvailable = false;
  loading = false;
  errorMsg = '';

  readonly stripeTestCards = [
    {
      number: '4242 4242 4242 4242',
      outcome: 'Pago exitoso',
      description: 'La transacción se aprueba sin autenticación adicional.',
      tone: 'success'
    },
    {
      number: '4000 0000 0000 0002',
      outcome: 'Tarjeta rechazada',
      description: 'Stripe responde con un rechazo genérico de la tarjeta.',
      tone: 'error'
    },
    {
      number: '4000 0000 0000 9995',
      outcome: 'Fondos insuficientes',
      description: 'Stripe rechaza el pago por falta de fondos.',
      tone: 'error'
    },
    {
      number: '4000 0025 0000 3155',
      outcome: 'Autenticación requerida',
      description: 'Simula un pago que solicita la validación 3D Secure.',
      tone: 'warning'
    }
  ];

  constructor(
    private http: HttpClient,
    private stripeService: StripeService
  ) {}

  async ngOnInit() {
    await this.initStripeFlow();
  }

  ngOnDestroy() {
    if (this.paymentElement) {
      try {
        this.paymentElement.destroy();
      } catch (e) {
        // Ignorar cleanup si ya fue destruido
      }
    }
  }

  /**
   * Inicializa el flujo solicitando el PaymentIntent al backend para montar Stripe Elements.
   */
  private async initStripeFlow() {
    this.initializingStripe = true;
    this.errorMsg = '';

    const publishableKey = this.stripeService.getPublishableKey();
    if (!publishableKey) {
      console.error('[PaymentModal] STRIPE_PUBLISHABLE_KEY no encontrada.');
      this.stripeAvailable = false;
      this.initializingStripe = false;
      this.errorMsg = 'La pasarela de pagos no se encuentra disponible temporalmente. Intenta recargar la página.';
      return;
    }

    try {
      this.stripe = await this.stripeService.getStripe();
      if (!this.stripe) {
        throw new Error('No se pudo inicializar Stripe.js.');
      }

      // Solicitamos al backend la creación del PaymentIntent para obtener el client_secret
      const idempotencyKey = crypto.randomUUID();
      const payload = {
        bookingId: this.bookingId,
        amount: this.amount
      };

      const response = await this.http.post<any>(apiBase() + '/payments/authorize', payload, {
        headers: { 'Idempotency-Key': idempotencyKey }
      }).toPromise();

      if (response?.status === 'DECLINED') {
        this.stripeAvailable = false;
        this.initializingStripe = false;
        this.errorMsg = response.message || 'La reserva no puede ser pagada porque su plazo ha expirado.';
        return;
      }

      if (!response?.clientSecret) {
        throw new Error('El servidor no retornó el clientSecret para la pasarela.');
      }

      this.clientSecret = response.clientSecret;

      // Configuración de apariencia de Stripe Elements acorde al diseño Notion/Clean de SkillBridge
      const appearance = {
        theme: 'stripe' as const,
        variables: {
          colorPrimary: '#111111',
          colorBackground: '#ffffff',
          colorText: '#111111',
          colorDanger: '#9F2F2D',
          fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
          spacingUnit: '4px',
          borderRadius: '6px'
        }
      };

      this.elements = this.stripe.elements({
        clientSecret: this.clientSecret,
        appearance
      });

      this.paymentElement = this.elements.create('payment', {
        layout: 'tabs'
      });

      this.stripeAvailable = true;
      this.initializingStripe = false;

      // Montamos el elemento en el DOM después de que Angular refresque la vista
      setTimeout(() => {
        if (this.paymentElementRef && this.paymentElement) {
          this.paymentElement.mount(this.paymentElementRef.nativeElement);
        }
      }, 50);

    } catch (err: any) {
      console.error('[PaymentModal Error]: Detalle técnico para desarrolladores:', err?.message || err);
      this.stripeAvailable = false;
      this.initializingStripe = false;
      this.errorMsg = 'No pudimos conectar con la pasarela de pagos segura en este momento. Por favor, intenta de nuevo o recarga la página.';
    }
  }

  /**
   * Ejecuta el pago mediante Stripe Elements.
   */
  async submitPayment() {
    this.loading = true;
    this.errorMsg = '';

    if (this.stripeAvailable && this.stripe && this.elements) {
      try {
        const { error, paymentIntent } = await this.stripe.confirmPayment({
          elements: this.elements,
          confirmParams: {
            return_url: this.getPaymentReturnUrl()
          },
          redirect: 'if_required'
        });

        if (error) {
          this.loading = false;
          console.error('[PaymentModal Error]: Detalle de error en confirmación:', error);
          // Stripe Elements ya muestra los errores de tarjeta y validación directamente bajo el campo afectado.
          // Solo asignamos errorMsg al banner superior si es un error general del sistema para evitar duplicidad.
          if (error.type !== 'card_error' && error.type !== 'validation_error') {
            this.errorMsg = error.message || 'El pago no pudo ser procesado. Intenta de nuevo.';
          }
          return;
        }

        if (paymentIntent && (paymentIntent.status === 'succeeded' || paymentIntent.status === 'processing')) {
          this.loading = false;
          this.paymentSuccess.emit();
        } else {
          this.loading = false;
          console.warn('[PaymentModal]: Estado de transacción no completado:', paymentIntent?.status);
          this.errorMsg = 'No fue posible completar la transacción. Por favor verifica tus fondos o intenta con otro medio de pago.';
        }
      } catch (err: any) {
        this.loading = false;
        console.error('[PaymentModal Error]: Detalle técnico al comunicar con la pasarela:', err?.message || err);
        this.errorMsg = 'Ocurrió un inconveniente al procesar el pago. Por favor intenta de nuevo en unos momentos.';
      }
    } else {
      this.loading = false;
      console.warn('[PaymentModal]: Stripe Elements no está disponible o no se configuró.');
      this.errorMsg = 'El servicio de pago no se encuentra disponible temporalmente. Cierra esta ventana, recarga la página e inténtalo de nuevo.';
    }
  }

  private getPaymentReturnUrl(): string {
    const returnUrl = new URL(window.location.href);
    returnUrl.searchParams.set('payment_booking_id', this.bookingId);
    return returnUrl.toString();
  }
}
