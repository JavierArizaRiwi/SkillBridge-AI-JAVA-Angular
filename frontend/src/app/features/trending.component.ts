import { Component, OnInit } from '@angular/core';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { TrendingService, TrendingServiceClient } from '../core/trending.service';

@Component({
  standalone: true,
  imports: [DecimalPipe, PercentPipe],
  template: `
    <section class="min-h-[calc(100vh-4rem)] bg-slate-50 px-4 py-12 sm:px-6 lg:py-20">
      <div class="mx-auto max-w-7xl">

        <!-- Header -->
        <div class="mb-10">
          <span class="inline-flex items-center rounded-full border border-blue-100 bg-blue-50 px-4 py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-blue-800">
            Análisis predictivo
          </span>
          <h1 class="mt-4 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Servicios en tendencia
          </h1>
          <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-500">
            Basado en recomendaciones y reservas de los últimos 14 días.
            El forecast estima la demanda para los próximos 7 días.
          </p>
        </div>

        <!-- Loading -->
        @if (loading) {
          <div class="flex items-center justify-center py-20">
            <svg class="h-8 w-8 animate-spin text-blue-600" xmlns="http://www.w3.org/2000/svg"
                 fill="none" viewBox="0 0 24 24" aria-label="Cargando">
              <circle class="opacity-25" cx="12" cy="12" r="10"
                      stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor"
                    d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"/>
            </svg>
            <span class="ml-3 text-sm text-slate-500">Calculando tendencias...</span>
          </div>
        }

        <!-- Error -->
        @if (error) {
          <div role="alert"
               class="rounded-xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700">
            {{ error }}
          </div>
        }

        <!-- Estado vacío -->
        @if (!loading && !error && services.length === 0) {
          <div class="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-20 text-center">
            <div class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-7 w-7 text-slate-400"
                   fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75z"/>
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="M9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625z"/>
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="M16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z"/>
              </svg>
            </div>
            <h3 class="text-base font-semibold text-slate-900">Sin datos aún</h3>
            <p class="mt-1 text-sm text-slate-500">
              Aún no hay suficiente actividad para calcular tendencias.
              Las métricas se generan a medida que los usuarios piden recomendaciones y hacen reservas.
            </p>
          </div>
        }

        <!-- Tabla de tendencias -->
        @if (!loading && !error && services.length > 0) {
          <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="overflow-x-auto">
              <table class="min-w-full divide-y divide-slate-200">
                <thead class="bg-slate-50">
                  <tr>
                    <th scope="col" class="px-6 py-3.5 text-left text-xs font-bold uppercase tracking-wide text-slate-500">#</th>
                    <th scope="col" class="px-6 py-3.5 text-left text-xs font-bold uppercase tracking-wide text-slate-500">Servicio</th>
                    <th scope="col" class="px-6 py-3.5 text-center text-xs font-bold uppercase tracking-wide text-slate-500">Trend Score</th>
                    <th scope="col" class="px-6 py-3.5 text-center text-xs font-bold uppercase tracking-wide text-slate-500">Forecast 7d</th>
                    <th scope="col" class="px-6 py-3.5 text-center text-xs font-bold uppercase tracking-wide text-slate-500">Recomendaciones</th>
                    <th scope="col" class="px-6 py-3.5 text-center text-xs font-bold uppercase tracking-wide text-slate-500">Reservas</th>
                    <th scope="col" class="px-6 py-3.5 text-center text-xs font-bold uppercase tracking-wide text-slate-500">Conversión</th>
                    <th scope="col" class="px-6 py-3.5 text-center text-xs font-bold uppercase tracking-wide text-slate-500">Crecimiento</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100 bg-white">
                  @for (svc of services; track svc.offeringId; let i = $index) {
                    <tr class="transition hover:bg-slate-50">

                      <!-- Ranking -->
                      <td class="px-6 py-4 text-sm font-bold text-slate-400">
                        {{ i + 1 }}
                      </td>

                      <!-- Nombre -->
                      <td class="px-6 py-4">
                        <span class="text-sm font-semibold text-slate-900">{{ svc.name }}</span>
                      </td>

                      <!-- Trend Score — barra de progreso -->
                      <td class="px-6 py-4">
                        <div class="flex flex-col items-center gap-1">
                          <span class="text-sm font-bold" [class]="scoreColor(svc.trendScore)">
                            {{ svc.trendScore | number:'1.1-1' }}
                          </span>
                          <div class="h-1.5 w-20 overflow-hidden rounded-full bg-slate-200">
                            <div class="h-full rounded-full transition-all duration-500"
                                 [class]="scoreBg(svc.trendScore)"
                                 [style.width.%]="svc.trendScore">
                            </div>
                          </div>
                        </div>
                      </td>

                      <!-- Forecast -->
                      <td class="px-6 py-4 text-center">
                        <span class="inline-flex items-center gap-1 rounded-lg bg-blue-50 px-3 py-1 text-sm font-bold text-blue-800">
                          {{ svc.forecastNext7Days | number:'1.0-0' }}
                          <span class="text-xs font-normal text-blue-500">reservas</span>
                        </span>
                      </td>

                      <!-- Recomendaciones -->
                      <td class="px-6 py-4 text-center text-sm text-slate-600">
                        {{ svc.recommendations | number }}
                      </td>

                      <!-- Reservas -->
                      <td class="px-6 py-4 text-center text-sm text-slate-600">
                        {{ svc.bookings | number }}
                      </td>

                      <!-- Conversion Rate -->
                      <td class="px-6 py-4 text-center">
                        <span class="text-sm font-semibold" [class]="conversionColor(svc.conversionRate)">
                          {{ svc.conversionRate | percent:'1.0-1' }}
                        </span>
                      </td>

                      <!-- Growth Rate -->
                      <td class="px-6 py-4 text-center">
                        <span class="inline-flex items-center gap-0.5 text-sm font-semibold"
                              [class]="growthColor(svc.growthRate)">
                          {{ svc.growthRate >= 0 ? '▲' : '▼' }}
                          {{ (svc.growthRate < 0 ? -svc.growthRate : svc.growthRate) | percent:'1.0-0' }}
                        </span>
                      </td>

                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>

          <!-- Leyenda de fórmulas -->
          <div class="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            @for (item of legend; track item.label) {
              <div class="rounded-xl border border-slate-200 bg-white px-4 py-3">
                <p class="text-xs font-bold uppercase tracking-wide text-slate-400">{{ item.label }}</p>
                <p class="mt-1 text-xs text-slate-500">{{ item.formula }}</p>
              </div>
            }
          </div>
        }

      </div>
    </section>
  `
})
export class TrendingComponent implements OnInit {
  services: TrendingService[] = [];
  loading = false;
  error = '';

  readonly legend = [
    { label: 'Trend Score',   formula: '50% conversión + 30% crecimiento + 20% volumen (0–100)' },
    { label: 'Forecast 7d',   formula: 'Promedio diario × 7 × (1 + max(0, crecimiento))' },
    { label: 'Conversión',    formula: 'Reservas efectivas / Recomendaciones' },
    { label: 'Crecimiento',   formula: '(Reservas 7d − Reservas 7d previos) / Reservas previos' },
  ];

  constructor(private trendingClient: TrendingServiceClient) {}

  ngOnInit(): void {
    this.loading = true;
    this.trendingClient.getTrending().subscribe({
      next: data => {
        this.services = data;
        this.loading = false;
      },
      error: () => {
        this.error = 'No fue posible cargar las tendencias. Verifica tu sesión.';
        this.loading = false;
      }
    });
  }

  scoreColor(score: number): string {
    if (score >= 70) return 'text-emerald-600';
    if (score >= 40) return 'text-amber-500';
    return 'text-slate-400';
  }

  scoreBg(score: number): string {
    if (score >= 70) return 'bg-emerald-500';
    if (score >= 40) return 'bg-amber-400';
    return 'bg-slate-300';
  }

  conversionColor(rate: number): string {
    if (rate >= 0.2) return 'text-emerald-600';
    if (rate >= 0.1) return 'text-amber-500';
    return 'text-slate-400';
  }

  growthColor(rate: number): string {
    if (rate > 0) return 'text-emerald-600';
    if (rate < 0) return 'text-red-500';
    return 'text-slate-400';
  }
}
