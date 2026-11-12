import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export interface Category {
  id: string;
  name: string;
  active: boolean;
  createdAt: string;
}

export interface CreateCategoryPayload {
  name: string;
}

@Injectable({ providedIn: 'root' })
export class CategoryService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<Category[]>(`${apiBase()}/categorias`);
  }

  create(payload: CreateCategoryPayload) {
    return this.http.post<Category>(`${apiBase()}/categorias`, payload);
  }
}
