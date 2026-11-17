import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';
import { Page } from './admin-user.service';

export type ProviderRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ProviderRequest {
  id: string;
  userId: string;
  userName?: string | null;
  userEmail?: string | null;
  status: ProviderRequestStatus;
  message: string;
  createdAt: string;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionReason?: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProviderRequestService {
  constructor(private http: HttpClient) {}

  create(message: string) {
    return this.http.post<ProviderRequest>(`${apiBase()}/provider-requests`, { message });
  }

  getMyRequests() {
    return this.http.get<ProviderRequest[]>(`${apiBase()}/provider-requests/me`);
  }

  listPending(page: number, size: number, sort: string) {
    return this.http.get<Page<ProviderRequest>>(`${apiBase()}/admin/provider-requests`, {
      params: { page, size, sort }
    });
  }

  approve(id: string, reason?: string) {
    return this.http.put<ProviderRequest>(`${apiBase()}/admin/provider-requests/${id}/approve`, {
      reason: reason || null
    });
  }

  reject(id: string, reason?: string) {
    return this.http.put<ProviderRequest>(`${apiBase()}/admin/provider-requests/${id}/reject`, {
      reason: reason || null
    });
  }
}
