import { TestBed } from '@angular/core/testing';
import { StripeService } from './stripe.service';

describe('StripeService', () => {
  let service: StripeService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(StripeService);
  });

  afterEach(() => {
    delete window.__env?.STRIPE_PUBLISHABLE_KEY;
  });

  it('debe crearse correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('debe retornar cadena vacía si no existe la clave pública en window.__env', () => {
    window.__env = { API_URL: '/api' };
    expect(service.getPublishableKey()).toBe('');
  });

  it('debe retornar la clave pública configurada en window.__env', () => {
    window.__env = {
      API_URL: '/api',
      STRIPE_PUBLISHABLE_KEY: 'pk_test_sample_123456789'
    };
    expect(service.getPublishableKey()).toBe('pk_test_sample_123456789');
  });

  it('debe retornar null en getStripe si no hay clave pública configurada', async () => {
    window.__env = { API_URL: '/api' };
    const stripeInstance = await service.getStripe();
    expect(stripeInstance).toBeNull();
  });
});
