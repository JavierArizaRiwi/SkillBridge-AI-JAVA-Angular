import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OfferingService, Offering } from './offering.service';
import { Page } from './page';

const pageOf = (items: Offering[] = []): Page<Offering> =>
  ({ items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 });

describe('OfferingService (paginación)', () => {
  let service: OfferingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OfferingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('list usa page=0, size=20 y sort=title,asc por defecto', () => {
    service.list().subscribe();
    const req = http.expectOne(r => r.url.endsWith('/offerings'));
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('title,asc');
    req.flush(pageOf());
  });

  it('list envía los parámetros indicados', () => {
    service.list(3, 9, 'price,desc').subscribe();
    const req = http.expectOne(r => r.url.endsWith('/offerings'));
    expect(req.request.params.get('page')).toBe('3');
    expect(req.request.params.get('size')).toBe('9');
    expect(req.request.params.get('sort')).toBe('price,desc');
    req.flush(pageOf());
  });

  it('listMine y listAll apuntan a sus endpoints paginados', () => {
    service.listMine(1, 10).subscribe();
    http.expectOne(r => r.url.endsWith('/offerings/me') && r.params.get('page') === '1').flush(pageOf());
    service.listAll(2, 10).subscribe();
    http.expectOne(r => r.url.endsWith('/admin/offerings') && r.params.get('page') === '2').flush(pageOf());
  });

  it('listForLookup pide el tamaño máximo y devuelve solo los items', () => {
    let result: Offering[] | undefined;
    service.listForLookup().subscribe(r => (result = r));
    const req = http.expectOne(r => r.url.endsWith('/offerings'));
    expect(req.request.params.get('size')).toBe('100');
    const item = { id: '1', title: 'Java', description: 'd', category: 'BACKEND', price: 10, active: true };
    req.flush(pageOf([item]));
    expect(result).toEqual([item]);
  });
});
