import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

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

  list() { return this.http.get<Offering[]>(`${apiBase()}/offerings`); }
  listMine() { return this.http.get<Offering[]>(`${apiBase()}/offerings/me`); }
  listAll() { return this.http.get<Offering[]>(`${apiBase()}/admin/offerings`); }
  create(p: OfferingPayload) { return this.http.post<Offering>(`${apiBase()}/offerings`, p); }
  update(id: string, p: OfferingPayload) { return this.http.put<Offering>(`${apiBase()}/offerings/${id}`, p); }
  deactivate(id: string) { return this.http.post<void>(`${apiBase()}/offerings/${id}/deactivate`, {}); }
  activate(id: string) { return this.http.post<void>(`${apiBase()}/offerings/${id}/activate`, {}); }
}
