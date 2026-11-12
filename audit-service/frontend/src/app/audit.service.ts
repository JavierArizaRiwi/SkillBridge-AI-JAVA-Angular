import { environment } from '../environments/environment';
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, interval, switchMap, startWith } from 'rxjs';

export interface BusinessEvent {
  eventId: string;
  eventType: string;
  aggregateId: string;
  aggregateType: string;
  occurredAt: string;
  correlationId: string;
  version: number;
  payload: any;
  
  // Extra fields that might be inside payload or flattened
  actorUserId?: string;
  actorUsername?: string;
  actorRole?: string;
  action?: string;
  resource?: string;
  resourceId?: string;
}

export interface AuditStats {
  totalEvents: number;
  bookingCreated: number;
  bookingCancelled: number;
  otherEvents: number;
  // If the API adds more in the future, we capture them
  [key: string]: any;
}

@Injectable({
  providedIn: 'root'
})
export class AuditService {
  private http = inject(HttpClient);
  private apiUrl = environment.auditApiUrl;

  getEvents(): Observable<BusinessEvent[]> {
    return this.http.get<BusinessEvent[]>(`${this.apiUrl}/events`);
  }

  getEvent(eventId: string): Observable<BusinessEvent> {
    return this.http.get<BusinessEvent>(`${this.apiUrl}/events/${eventId}`);
  }

  getStats(): Observable<AuditStats> {
    return this.http.get<AuditStats>(`${this.apiUrl}/stats`);
  }
}
