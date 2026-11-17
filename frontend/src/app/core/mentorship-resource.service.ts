import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiBase } from './api';

export type ResourceType = 'VIDEO' | 'DOCUMENT' | 'LINK';
export interface MentorshipResource { id: string; offeringId: string; title: string; resourceType: ResourceType; url: string; }
export interface ResourcePayload { title: string; resourceType: ResourceType; url: string; }

@Injectable({ providedIn: 'root' })
export class MentorshipResourceService {
  constructor(private http: HttpClient) { }
  listForOffering(offeringId: string) { return this.http.get<MentorshipResource[]>(`${apiBase()}/mentorship-resources/offerings/${offeringId}`); }
  create(offeringId: string, payload: ResourcePayload) { return this.http.post<MentorshipResource>(`${apiBase()}/mentorship-resources/offerings/${offeringId}`, payload); }
  delete(id: string) { return this.http.delete<void>(`${apiBase()}/mentorship-resources/${id}`); }
}
