import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';
import { MAX_PAGE_SIZE, Page } from './page';
import { map } from 'rxjs';

export interface Offering {
  id: string;
  providerId?: string;
  title: string;
  description: string;
  category: string;
  price: number;
  active: boolean;
}

export interface OfferingPayload {
  title: string;
  description: string;
  category: string;
  price: number;
  providerId?: string; // solo lo usa el Admin al crear
}

@Injectable({ providedIn: 'root' })
export class OfferingService {
  constructor(private http: HttpClient) {}

  list(page = 0, size = 20, sort = 'title,asc') {
    return this.http.get<Page<Offering>>(`${apiBase()}/offerings`, { params: { page, size, sort } });
  }
  listMine(page = 0, size = 20, sort = 'title,asc') {
    return this.http.get<Page<Offering>>(`${apiBase()}/offerings/me`, { params: { page, size, sort } });
  }
  listAll(page = 0, size = 20, sort = 'title,asc') {
    return this.http.get<Page<Offering>>(`${apiBase()}/admin/offerings`, { params: { page, size, sort } });
  }
  /** Primeros MAX_PAGE_SIZE servicios activos, para selectores y búsquedas por id. */
  listForLookup() { return this.list(0, MAX_PAGE_SIZE).pipe(map(p => p.items)); }
  create(p: OfferingPayload) { return this.http.post<Offering>(`${apiBase()}/offerings`, p); }
  update(id: string, p: OfferingPayload) { return this.http.put<Offering>(`${apiBase()}/offerings/${id}`, p); }
  deactivate(id: string) { return this.http.post<void>(`${apiBase()}/offerings/${id}/deactivate`, {}); }
  activate(id: string) { return this.http.post<void>(`${apiBase()}/offerings/${id}/activate`, {}); }
}
