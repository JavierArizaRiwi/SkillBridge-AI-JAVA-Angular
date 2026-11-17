import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export interface Category {
  id: string;
  name: string;
}

@Injectable({
  providedIn: 'root',
})
export class CategoryService {
  private http = inject(HttpClient);
  private apiUrl = `${apiBase()}/categories`;

  list(): Observable<Category[]> {
    return this.http.get<Category[]>(this.apiUrl);
  }

  create(payload: { name: string }): Observable<Category> {
    return this.http.post<Category>(this.apiUrl, payload);
  }

  update(id: string, payload: { name: string }): Observable<Category> {
    return this.http.put<Category>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}