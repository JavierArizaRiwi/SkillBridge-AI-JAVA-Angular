import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { apiBase } from './api';
import { Observable } from 'rxjs';


export type BookingStatus = 'CREATED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export interface Booking {
  id: string;
  offeringId: string;
  customerId: string;
  scheduledAt: string;
  status: BookingStatus;
}

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(private http: HttpClient) {}

  // Obtiene las reservas pertenecientes al usuario autenticado.
  getMyBookings(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${apiBase()}/bookings/me`);
  }
}