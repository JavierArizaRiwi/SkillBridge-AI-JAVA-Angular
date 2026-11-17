import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BookingService } from './booking.service';

describe('BookingService (paginación)', () => {
  let service: BookingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BookingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getMyBookings pide la primera página ordenada por fecha descendente', () => {
    service.getMyBookings().subscribe();
    const req = http.expectOne(r => r.url.endsWith('/bookings/me'));
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('scheduledAt,desc');
    req.flush({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('getMyBookings propaga page y size', () => {
    service.getMyBookings(2, 100).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/bookings/me'));
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('100');
    req.flush({ items: [], page: 2, size: 100, totalElements: 0, totalPages: 0 });
  });
});
