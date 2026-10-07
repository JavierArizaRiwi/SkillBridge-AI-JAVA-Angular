import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Offering, OfferingService } from '../core/offering.service';
import { apiBase } from '../core/api';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="container section">
      <div class="card booking-card">
        <p class="eyebrow">EVENT-DRIVEN FLOW</p>
        <h1>Reservar una sesión</h1>
        <p class="muted">Al confirmar, Spring persiste la reserva y publica un evento <code>BookingCreated</code> en RabbitMQ.</p>

        <label class="field">
          Servicio
          <select [(ngModel)]="offeringId">
            <option value="" disabled>Selecciona un servicio</option>
            @for (offering of offerings; track offering.id) {
              <option [value]="offering.id">{{ offering.title }}</option>
            }
          </select>
        </label>

        <label class="field">
          Fecha y hora
          <input type="datetime-local" [(ngModel)]="scheduledLocal">
        </label>

        <button class="btn" [disabled]="loading" (click)="book()">
          {{ loading ? 'Creando...' : 'Crear reserva' }}
        </button>

        @if (error) { <p class="error">{{ error }}</p> }
        @if (success) { <p class="success">{{ success }}</p> }
      </div>
    </section>
  `,
  styles: [`.section{padding:50px 0}.booking-card{max-width:700px;margin:auto}.eyebrow{font-weight:800;letter-spacing:.1em}.field select{border:1px solid #cfd8e6;border-radius:10px;padding:11px;background:white}`]
})
export class BookingComponent implements OnInit {
  offerings: Offering[] = [];
  offeringId = '';
  scheduledLocal = '';
  loading = false;
  error = '';
  success = '';

  constructor(private offeringsService: OfferingService, private http: HttpClient) {}

  ngOnInit(): void {
    this.offeringsService.list().subscribe({
      next: list => this.offerings = list,
      error: () => this.error = 'No fue posible cargar los servicios.'
    });
  }

  book(): void {
    this.error = '';
    this.success = '';
    if (!this.offeringId || !this.scheduledLocal) {
      this.error = 'Selecciona un servicio y una fecha.';
      return;
    }

    this.loading = true;
    const scheduledAt = new Date(this.scheduledLocal).toISOString();
    this.http.post<{id: string}>(`${apiBase()}/bookings`, { offeringId: this.offeringId, scheduledAt })
      .subscribe({
        next: booking => {
          this.success = `Reserva creada exitosamente`;
          this.offeringId = '';
          this.scheduledLocal = '';
          this.loading = false;
        },
        error: e => {
          this.error = e?.error?.detail || 'No fue posible crear la reserva. Inicia sesión y verifica la fecha.';
          this.loading = false;
        }
      });
  }
}
