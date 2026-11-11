import { Component, OnInit, inject } from "@angular/core";
import { CurrencyPipe } from "@angular/common";
import { Router } from "@angular/router";
import { Offering, OfferingService } from "../core/offering.service";

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CurrencyPipe],
  template: `
    <!-- Hero: un solo campo de color, tipografía grande, sin adornos -->
    <section class="bg-neutral-950 text-neutral-50">
      <div class="mx-auto max-w-7xl px-4 pb-20 pt-24 sm:px-6 sm:pb-28 sm:pt-32 lg:px-8">
        <h1 class="enter-rise max-w-5xl text-5xl font-semibold leading-[1.02] tracking-[-0.04em] sm:text-6xl lg:text-7xl">
          Servicios y experiencias en una sola plataforma.
        </h1>
        <p class="enter-rise mt-8 max-w-xl text-lg leading-8 text-neutral-300" style="animation-delay: 70ms">
          Descubre servicios, eventos y experiencias disponibles. Una plataforma construida con Angular y Spring Boot.
        </p>
        <p class="enter-rise mt-10 text-sm text-neutral-500" style="animation-delay: 140ms">
          Angular · Spring Boot · Arquitectura hexagonal · Cloud
        </p>
      </div>
    </section>

    <!-- Catálogo -->
    <section class="bg-neutral-100 px-4 py-20 sm:px-6 lg:px-8 lg:py-28">
      <div class="mx-auto max-w-7xl">
        <div class="mb-12 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 class="text-4xl font-semibold tracking-[-0.03em] text-neutral-950 sm:text-5xl">Servicios disponibles</h2>
            <p class="mt-4 max-w-xl text-base leading-7 text-neutral-700">
              Encuentra el servicio que necesitas y descubre las opciones disponibles para reservar.
            </p>
          </div>

          @if (offerings.length > 0) {
            <span class="text-sm font-medium text-neutral-600 tabular-nums">{{ offerings.length }} servicios</span>
          }
        </div>

        @if (error) {
          <div role="alert" class="enter-rise mb-8 flex items-center gap-3 rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-800">
            <svg class="h-5 w-5 shrink-0 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
            </svg>
            <span>{{ error }}</span>
          </div>
        }

        @if (loading) {
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
            @for (i of skeletonPlaceholders; track i) {
              <div class="animate-pulse rounded-3xl bg-white p-7">
                <div class="h-4 w-20 rounded-full bg-neutral-200"></div>
                <div class="mt-6 h-6 w-3/4 rounded-full bg-neutral-200"></div>
                <div class="mt-4 h-4 w-full rounded-full bg-neutral-100"></div>
                <div class="mt-2 h-4 w-5/6 rounded-full bg-neutral-100"></div>
                <div class="mt-10 flex items-end justify-between">
                  <div class="h-7 w-24 rounded-full bg-neutral-200"></div>
                  <div class="h-10 w-28 rounded-full bg-neutral-200"></div>
                </div>
              </div>
            }
          </div>
        }

        @if (!loading) {
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          @for (offering of offerings; track offering.id; let i = $index) {
            <article class="enter-rise group flex flex-col rounded-3xl bg-white p-7 transition-transform duration-200 ease-[cubic-bezier(0.23,1,0.32,1)] [@media(hover:hover)]:hover:scale-[1.01] motion-reduce:transition-none"
                     [style.animation-delay.ms]="(i < 6 ? i : 6) * 60">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium text-neutral-500">{{ offering.category }}</span>
                <span class="h-2 w-2 rounded-full bg-emerald-600" title="Disponible"></span>
              </div>

              <h3 class="mt-6 text-2xl font-semibold leading-tight tracking-[-0.02em] text-neutral-950">
                {{ offering.title }}
              </h3>

              <p class="mt-3 flex-1 text-base leading-7 text-neutral-700">
                {{ offering.description }}
              </p>

              <div class="mt-10 flex items-end justify-between gap-4">
                <div>
                  <p class="text-sm text-neutral-500">Desde</p>
                  <strong class="mt-0.5 block text-2xl font-semibold tracking-[-0.02em] text-neutral-950 tabular-nums">
                    {{ offering.price | currency: "COP" : "symbol-narrow" : "1.0-0" }}
                  </strong>
                </div>

                <button
                  type="button"
                  (click)="bookOffering(offering)"
                  class="cursor-pointer rounded-full bg-neutral-900 px-5 py-2.5 text-sm font-medium text-white transition-[transform,background-color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-neutral-700 [@media(hover:hover)]:hover:scale-[1.03] active:scale-[0.97] motion-reduce:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
                >
                  Reservar
                </button>
              </div>
            </article>
          }
        </div>

        @if (!loading && !error && offerings.length === 0) {
          <div class="rounded-3xl bg-white px-6 py-20 text-center">
            <h3 class="text-xl font-semibold tracking-[-0.02em] text-neutral-950">No hay servicios disponibles</h3>
            <p class="mt-2 text-base text-neutral-600">Actualmente no encontramos servicios en el catálogo.</p>
          </div>
        }
        }
      </div>
    </section>
  `,
})
export class HomeComponent implements OnInit {
  offerings: Offering[] = [];
  error = "";
  loading = true;
  readonly skeletonPlaceholders = [0, 1, 2];

  private service = inject(OfferingService);
  private router = inject(Router);

  ngOnInit(): void {
    this.service.list().subscribe({
      next: (r) => { this.offerings = r; this.loading = false; },
      error: () => { this.error = "No fue posible cargar el catálogo."; this.loading = false; },
    });
  }

  // Redirige al formulario de reservas enviando el ID del servicio en la URL
  bookOffering(offering: Offering): void {
    this.router.navigate(['/book'], {
      queryParams: { offeringId: offering.id }
    });
  }
}