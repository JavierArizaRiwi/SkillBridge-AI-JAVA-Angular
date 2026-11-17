import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminProviderRequestsComponent } from './admin-provider-requests.component';
import { ProviderRequestService, ProviderRequest } from '../core/provider-request.service';
import { AuthService } from '../core/auth.service';
import { of } from 'rxjs';
import { Page } from '../core/admin-user.service';

describe('AdminProviderRequestsComponent', () => {
  let component: AdminProviderRequestsComponent;
  let fixture: ComponentFixture<AdminProviderRequestsComponent>;
  let mockService: jasmine.SpyObj<ProviderRequestService>;
  let mockAuth: Partial<AuthService>;

  const pendingRequest: ProviderRequest = {
    id: 'req-10',
    userId: 'user-10',
    userName: 'Laura Gómez',
    userEmail: 'laura@test.com',
    status: 'PENDING',
    message: 'Tutorías de matemáticas avanzadas',
    createdAt: '2026-11-11T12:00:00Z',
  };

  const pageData: Page<ProviderRequest> = {
    items: [pendingRequest],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  };

  beforeEach(async () => {
    mockService = jasmine.createSpyObj('ProviderRequestService', ['listPending', 'approve', 'reject']);
    mockAuth = {
      hasRole: jasmine.createSpy('hasRole').and.returnValue(true),
      email: jasmine.createSpy('email').and.returnValue('admin@skillbridge.ai'),
    };

    await TestBed.configureTestingModule({
      imports: [AdminProviderRequestsComponent],
      providers: [
        { provide: ProviderRequestService, useValue: mockService },
        { provide: AuthService, useValue: mockAuth },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminProviderRequestsComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse y cargar las solicitudes pendientes', () => {
    mockService.listPending.and.returnValue(of(pageData));
    fixture.detectChanges();

    expect(component).toBeTruthy();
    expect(component.requests.length).toBe(1);
    expect(component.totalElements).toBe(1);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Laura Gómez');
    expect(compiled.textContent).toContain('Tutorías de matemáticas avanzadas');
  });

  it('debe confirmar aprobación y llamar a service.approve', () => {
    mockService.listPending.and.returnValue(of(pageData));
    mockService.approve.and.returnValue(of({ ...pendingRequest, status: 'APPROVED' }));
    fixture.detectChanges();

    component.openApproveModal(pendingRequest);
    component.decisionReason = 'Documentos válidos';
    component.confirmApprove();

    expect(mockService.approve).toHaveBeenCalledWith('req-10', 'Documentos válidos');
    expect(component.notice).toContain('aprobada exitosamente');
  });

  it('debe confirmar rechazo y llamar a service.reject', () => {
    mockService.listPending.and.returnValue(of(pageData));
    mockService.reject.and.returnValue(of({ ...pendingRequest, status: 'REJECTED' }));
    fixture.detectChanges();

    component.openRejectModal(pendingRequest);
    component.decisionReason = 'Falta experiencia comprobable';
    component.confirmReject();

    expect(mockService.reject).toHaveBeenCalledWith('req-10', 'Falta experiencia comprobable');
    expect(component.notice).toContain('rechazada');
  });
});
