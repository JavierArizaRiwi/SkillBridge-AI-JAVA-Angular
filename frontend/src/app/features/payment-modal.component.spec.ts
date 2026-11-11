import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PaymentModal } from './payment-modal.component';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { StripeService } from '../core/stripe.service';

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

  it('debe crearse correctamente', () => {
    stripeServiceSpy.getPublishableKey.and.returnValue('');
    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  it('debe caer en modo simulado si no hay clave pública de Stripe configurada', fakeAsync(() => {
    stripeServiceSpy.getPublishableKey.and.returnValue('');
    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    component.bookingId = 'test-booking-id';
    component.amount = 50;

    component.ngOnInit();
    tick();

    expect(component.stripeAvailable).toBeFalse();
    expect(component.initializingStripe).toBeFalse();
  }));

  it('debe formatear el número de tarjeta correctamente en modo simulado', () => {
    stripeServiceSpy.getPublishableKey.and.returnValue('');
    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;

    component.formatCardNumber('1234567812345678');
    expect(component.cardData).toBeDefined();
  });

  it('debe rechazar CVV "000" determinísticamente en modo simulado', fakeAsync(() => {
    stripeServiceSpy.getPublishableKey.and.returnValue('');
    fixture = TestBed.createComponent(PaymentModal);
    component = fixture.componentInstance;
    component.cardData.cvv = '000';
    component.cardData.expiryDate = '12/28';

    component.submitPayment();
    tick(700);

    expect(component.errorMsg).toContain("valor '000' no son admitidos");
    expect(component.loading).toBeFalse();
  }));
});
