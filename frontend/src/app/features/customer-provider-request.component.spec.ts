import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CustomerProviderRequestComponent } from './customer-provider-request.component';
import { ProviderRequestService, ProviderRequest } from '../core/provider-request.service';
import { AuthService } from '../core/auth.service';
import { of, throwError } from 'rxjs';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';

describe('CustomerProviderRequestComponent', () => {
  let component: CustomerProviderRequestComponent;
  let fixture: ComponentFixture<CustomerProviderRequestComponent>;
  let mockService: jasmine.SpyObj<ProviderRequestService>;
  let mockAuth: Partial<AuthService>;

  const pendingRequest: ProviderRequest = {
    id: 'req-1',
    userId: 'user-1',
    status: 'PENDING',
    message: 'Quiero enseñar TypeScript',
    createdAt: '2026-11-11T12:00:00Z',
  };

  beforeEach(async () => {
    mockService = jasmine.createSpyObj('ProviderRequestService', ['getMyRequests', 'create']);
    mockAuth = {
      hasRole: jasmine.createSpy('hasRole').and.returnValue(false),
      role: signal('CUSTOMER'),
    };

    await TestBed.configureTestingModule({
      imports: [CustomerProviderRequestComponent],
      providers: [
        provideRouter([]),
        { provide: ProviderRequestService, useValue: mockService },
        { provide: AuthService, useValue: mockAuth },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerProviderRequestComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse correctamente', () => {
    mockService.getMyRequests.and.returnValue(of([]));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('debe detectar solicitud pendiente al cargar', () => {
    mockService.getMyRequests.and.returnValue(of([pendingRequest]));
    fixture.detectChanges();

    expect(component.pendingRequest).toEqual(pendingRequest);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Tu solicitud está en evaluación');
    expect(compiled.textContent).toContain('Quiero enseñar TypeScript');
  });

  it('debe permitir enviar una solicitud si no hay pendiente', () => {
    mockService.getMyRequests.and.returnValue(of([]));
    mockService.create.and.returnValue(of(pendingRequest));
    fixture.detectChanges();

    component.message = 'Nueva propuesta de clases';
    component.submitRequest();

    expect(mockService.create).toHaveBeenCalledWith('Nueva propuesta de clases');
    expect(component.notice).toContain('¡Solicitud enviada con éxito!');
  });

  it('debe mostrar mensaje de error si falla la creación', () => {
    mockService.getMyRequests.and.returnValue(of([]));
    mockService.create.and.returnValue(
      throwError(() => ({ error: { detail: 'Ya existe una solicitud pendiente' } }))
    );
    fixture.detectChanges();

    component.message = 'Propuesta duplicada';
    component.submitRequest();

    expect(component.error).toBe('Ya existe una solicitud pendiente');
  });
});
