import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';
import { Role } from './auth.service';
import { Page } from './page';

export type { Page } from './page';

export type UserStatus = 'ACTIVE' | 'SUSPENDED';

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: UserStatus;
}

@Injectable({ providedIn: 'root' })
export class AdminUserService {
  constructor(private http: HttpClient) {}

  list(page: number, size: number, sort: string) {
    return this.http.get<Page<AdminUser>>(`${apiBase()}/admin/users`, { params: { page, size, sort } });
  }
  detail(id: string) { return this.http.get<AdminUser>(`${apiBase()}/admin/users/${id}`); }
  changeStatus(id: string, status: UserStatus) {
    return this.http.put<AdminUser>(`${apiBase()}/admin/users/${id}/status`, { status });
  }
  changeRole(id: string, role: Role) {
    return this.http.put<AdminUser>(`${apiBase()}/admin/users/${id}/role`, { role });
  }
}
