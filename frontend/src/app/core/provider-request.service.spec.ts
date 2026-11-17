import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProviderRequest, ProviderRequestService } from './provider-request.service';
import { Page } from './admin-user.service';

describe('ProviderRequestService', () => {
  let service: ProviderRequestService;
  let httpMock: HttpTestingController;

  const mockRequest: ProviderRequest = {
    id: 'req-1',
    userId: 'user-1',
    userName: 'Juan',
    userEmail: 'juan@test.com',
    status: 'PENDING',
    message: 'Quiero ofrecer mentorías',
    createdAt: '2026-11-11T12:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ProviderRequestService,
      ],
    });
    service = TestBed.inject(ProviderRequestService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('debe crearse correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('create debe enviar POST con el mensaje a /provider-requests', () => {
    service.create('Quiero ofrecer mentorías').subscribe((res) => {
      expect(res).toEqual(mockRequest);
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/provider-requests') && r.method === 'POST');
    expect(req.request.body).toEqual({ message: 'Quiero ofrecer mentorías' });
    req.flush(mockRequest);
  });

  it('getMyRequests debe enviar GET a /provider-requests/me', () => {
    service.getMyRequests().subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].id).toBe('req-1');
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/provider-requests/me') && r.method === 'GET');
    req.flush([mockRequest]);
  });

  it('listPending debe enviar GET a /admin/provider-requests con paginación y orden', () => {
    const pageResult: Page<ProviderRequest> = {
      items: [mockRequest],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    };

    service.listPending(0, 10, 'createdAt,asc').subscribe((res) => {
      expect(res.items.length).toBe(1);
      expect(res.totalElements).toBe(1);
    });

    const req = httpMock.expectOne(
      (r) =>
        r.url.endsWith('/admin/provider-requests') &&
        r.method === 'GET' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10' &&
        r.params.get('sort') === 'createdAt,asc'
    );
    req.flush(pageResult);
  });

  it('approve debe enviar PUT a /admin/provider-requests/:id/approve', () => {
    const approved: ProviderRequest = {
      ...mockRequest,
      status: 'APPROVED',
      decidedBy: 'admin-1',
      decisionReason: 'Verificado',
    };

    service.approve('req-1', 'Verificado').subscribe((res) => {
      expect(res.status).toBe('APPROVED');
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/admin/provider-requests/req-1/approve') && r.method === 'PUT');
    expect(req.request.body).toEqual({ reason: 'Verificado' });
    req.flush(approved);
  });

  it('reject debe enviar PUT a /admin/provider-requests/:id/reject', () => {
    const rejected: ProviderRequest = {
      ...mockRequest,
      status: 'REJECTED',
      decidedBy: 'admin-1',
      decisionReason: 'Incompleto',
    };

    service.reject('req-1', 'Incompleto').subscribe((res) => {
      expect(res.status).toBe('REJECTED');
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/admin/provider-requests/req-1/reject') && r.method === 'PUT');
    expect(req.request.body).toEqual({ reason: 'Incompleto' });
    req.flush(rejected);
  });
});
