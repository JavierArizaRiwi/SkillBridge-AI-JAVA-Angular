import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiBase } from './api';

export interface TrendingService {
  offeringId: string;
  name: string;
  trendScore: number;
  forecastNext7Days: number;
  recommendations: number;
  bookings: number;
  conversionRate: number;
  growthRate: number;
}

@Injectable({ providedIn: 'root' })
export class TrendingServiceClient {
  constructor(private http: HttpClient) {}

  getTrending(): Observable<TrendingService[]> {
    return this.http.get<TrendingService[]>(`${apiBase()}/ai/trending`);
  }
}
