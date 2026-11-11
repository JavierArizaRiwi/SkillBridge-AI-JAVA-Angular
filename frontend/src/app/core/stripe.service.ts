import { Injectable } from '@angular/core';
import { loadStripe, Stripe, StripeElements, StripePaymentElement } from '@stripe/stripe-js';

/**
 * Servicio encargado de inicializar y gestionar la instancia de Stripe.js en el Frontend.
 * Carga la librería de manera segura utilizando la clave pública dinámica desde las variables de entorno.
 */
@Injectable({
  providedIn: 'root'
})
export class StripeService {
  private stripePromise: Promise<Stripe | null> | null = null;

  /**
   * Obtiene la clave pública desde el entorno (window.__env o fallback).
   */
  getPublishableKey(): string {
    return window.__env?.STRIPE_PUBLISHABLE_KEY || '';
  }

  /**
   * Inicializa o retorna la promesa singleton de la instancia de Stripe.
   */
  getStripe(): Promise<Stripe | null> {
    if (!this.stripePromise) {
      const key = this.getPublishableKey();
      if (!key) {
        console.warn('[StripeService] STRIPE_PUBLISHABLE_KEY no está configurada en window.__env');
        return Promise.resolve(null);
      }
      this.stripePromise = loadStripe(key, {
        developerTools: {
          assistant: {
            enabled: false
          }
        }
      });
    }
    return this.stripePromise;
  }
}
