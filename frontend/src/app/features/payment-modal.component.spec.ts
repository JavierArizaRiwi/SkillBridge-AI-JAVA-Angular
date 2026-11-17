import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PaymentModal } from './payment-modal.component';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { StripeService } from '../core/stripe.service';
import { apiBase } from '../core/api';

describe('PaymentModal', () => {
  let component: PaymentModal;
  let fixture: ComponentFixture<PaymentModal>;
  let httpMock: HttpTestingController;
  let stripeServiceSpy: jasmine.SpyObj<StripeService>;

  beforeEach(async () => {
    stripeServiceSpy = jasmine.createSpyObj('StripeService', ['getPublishableKey', 'getStripe']);

    await TestBed.configureTestingModule({
      imports: [PaymentModal, HttpClientTestingModule],
      providers: [
        { provide: StripeService, useValue: stripeServiceSpy }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // [Básico]: Comprueba que el componente abra sin errores en Angular.
  it('debe crearse correctamente en pantalla', () => {
    stripeServiceSpy.getPublishableKey.and.returnValue('');
    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  // [Caso Triste]: Si falta la clave pública en el entorno, desactiva el botón y muestra error amigable.
  it('debe avisar amablemente si la clave pública de Stripe no está configurada', fakeAsync(() => {
    stripeServiceSpy.getPublishableKey.and.returnValue('');
    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    component.bookingId = 'test-booking-id';
    component.amount = 50;

    component.ngOnInit();
    tick();

    expect(component.stripeAvailable).toBeFalse();
    expect(component.initializingStripe).toBeFalse();
    expect(component.errorMsg).toContain('no se encuentra disponible temporalmente');
  }));

  // [Caso Feliz]: Pide el clientSecret al backend y monta los campos seguros de Stripe Elements.
  it('debe montar Stripe Elements cuando el backend responde con éxito', fakeAsync(() => {
    stripeServiceSpy.getPublishableKey.and.returnValue('pk_test_sample_key');
    const mockPaymentElement = {
      mount: jasmine.createSpy('mount'),
      destroy: jasmine.createSpy('destroy')
    };
    const mockElements = {
      create: jasmine.createSpy('create').and.returnValue(mockPaymentElement)
    };
    const mockStripe = {
      elements: jasmine.createSpy('elements').and.returnValue(mockElements)
    };
    stripeServiceSpy.getStripe.and.resolveTo(mockStripe as any);

    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    component.bookingId = 'test-booking-id';
    component.amount = 100;

    component.ngOnInit();
    tick();

    const req = httpMock.expectOne(apiBase() + '/payments/authorize');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ bookingId: 'test-booking-id', amount: 100 });
    req.flush({ clientSecret: 'pi_test_secret_123' });
    tick(50);

    expect(component.stripeAvailable).toBeTrue();
    expect(component.clientSecret).toBe('pi_test_secret_123');
    expect(mockStripe.elements).toHaveBeenCalled();
    expect(mockElements.create).toHaveBeenCalledWith('payment', { layout: 'tabs' });
  }));

  // [Caso Triste]: Si el backend falla o no hay conexión, muestra un mensaje amigable al usuario.
  it('debe mostrar mensaje amigable si el backend falla al iniciar el pago', fakeAsync(() => {
    stripeServiceSpy.getPublishableKey.and.returnValue('pk_test_sample_key');
    const mockStripe = {
      elements: jasmine.createSpy('elements')
    };
    stripeServiceSpy.getStripe.and.resolveTo(mockStripe as any);

    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    component.bookingId = 'test-booking-id';
    component.amount = 100;

    component.ngOnInit();
    tick();

    const req = httpMock.expectOne(apiBase() + '/payments/authorize');
    req.error(new ProgressEvent('Network error'));
    tick();

    expect(component.stripeAvailable).toBeFalse();
    expect(component.errorMsg).toContain('No pudimos conectar con la pasarela de pagos');
  }));

  // [Caso Triste / Lógica 12h]: Si el backend responde DECLINED porque la reserva expiró o fue cancelada
  it('debe mostrar mensaje amigable si el backend responde DECLINED por plazo expirado', fakeAsync(() => {
    stripeServiceSpy.getPublishableKey.and.returnValue('pk_test_sample_key');
    const mockStripe = { elements: jasmine.createSpy('elements') };
    stripeServiceSpy.getStripe.and.resolveTo(mockStripe as any);

    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    component.bookingId = 'test-booking-id';
    component.amount = 100;

    component.ngOnInit();
    tick();

    const req = httpMock.expectOne(apiBase() + '/payments/authorize');
    req.flush({
      status: 'DECLINED',
      message: 'El plazo para pagar esta reserva ha expirado (menos de 12 horas para la sesión). La reserva ha sido cancelada.'
    });
    tick();

    expect(component.stripeAvailable).toBeFalse();
    expect(component.initializingStripe).toBeFalse();
    expect(component.errorMsg).toContain('menos de 12 horas para la sesión');
  }));

  // [Caso de Validación]: No duplica el mensaje en el banner superior si Stripe ya lo reporta como card_error
  it('no debe duplicar el mensaje superior si Stripe reporta card_error', async () => {
    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    component.stripeAvailable = true;
    const mockStripe = {
      confirmPayment: jasmine.createSpy('confirmPayment').and.resolveTo({
        error: { type: 'card_error', message: 'Tu tarjeta ha sido rechazada.' }
      })
    };
    component.stripe = mockStripe as any;
    component.elements = {} as any;

    await component.submitPayment();

    expect(component.loading).toBeFalse();
    expect(component.errorMsg).toBe('');
  });
});
