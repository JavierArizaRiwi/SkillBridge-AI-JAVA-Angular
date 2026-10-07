import { Component, OnInit } from "@angular/core";
import { CurrencyPipe } from "@angular/common";
import { Offering, OfferingService } from "../core/offering.service";

@Component({
  standalone: true,
  imports: [CurrencyPipe],
  template: `
    <!-- Hero principal -->
    <section class="relative overflow-hidden bg-slate-950 text-white">

      <!-- Elementos decorativos del fondo -->
      <div
        class="absolute -right-40 -top-40 h-96 w-96 rounded-full bg-blue-600/20 blur-3xl"
      ></div>

      <div
        class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-indigo-500/10 blur-3xl"
      ></div>

      <!-- Contenido del hero -->
      <div class="relative mx-auto max-w-7xl px-4 py-20 sm:px-6 sm:py-28 lg:px-8">

        <div class="max-w-4xl">

          <!-- Etiqueta superior -->
          <span
            class="inline-flex items-center rounded-full border border-blue-400/30 bg-blue-400/10 px-4 py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-blue-300"
          >
            Proyecto integrador
          </span>

          <!-- Título principal -->
          <h1
            class="mt-6 max-w-4xl text-4xl font-bold tracking-tight sm:text-5xl lg:text-6xl"
          >
            Servicios y experiencias
            <span class="text-blue-400">
              en una sola plataforma.
            </span>
          </h1>

          <!-- Descripción -->
          <p
            class="mt-6 max-w-2xl text-base leading-7 text-slate-300 sm:text-lg"
          >
            Descubre servicios, eventos y experiencias disponibles.
            Una plataforma construida con Angular y Spring Boot,
            diseñada para ofrecer una experiencia de reserva sencilla
            y profesional.
          </p>

          <!-- Información técnica -->
          <div class="mt-8 flex flex-wrap gap-3">

            <span
              class="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-300"
            >
              Angular
            </span>

            <span
              class="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-300"
            >
              Spring Boot
            </span>

            <span
              class="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-300"
            >
              Arquitectura hexagonal
            </span>

            <span
              class="rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-sm text-slate-300"
            >
              Cloud
            </span>

          </div>
        </div>
      </div>
    </section>

    <!-- Catálogo -->
    <section class="bg-slate-50 px-4 py-14 sm:px-6 lg:px-8 lg:py-20">

      <div class="mx-auto max-w-7xl">

        <!-- Encabezado del catálogo -->
        <div class="mb-10 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">

          <div>
            <p
              class="text-sm font-bold uppercase tracking-[0.15em] text-blue-800"
            >
              Explora nuestras opciones
            </p>

            <h2
              class="mt-2 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl"
            >
              Servicios disponibles
            </h2>

            <p class="mt-3 max-w-2xl text-sm leading-6 text-slate-500 sm:text-base">
              Encuentra el servicio que necesitas y descubre
              las opciones disponibles para reservar.
            </p>
          </div>

          <!-- Cantidad de servicios -->
          @if (offerings.length > 0) {
            <span
              class="w-fit rounded-full bg-white px-4 py-2 text-sm font-medium text-slate-600 shadow-sm ring-1 ring-slate-200"
            >
              {{ offerings.length }} servicios
            </span>
          }

        </div>

        <!-- Error -->
        @if (error) {
          <div
            role="alert"
            class="mb-8 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
          >
            {{ error }}
          </div>
        }

        <!-- Grid de servicios -->
        <div
          class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
        >

          @for (offering of offerings; track offering.id) {

            <!-- Card -->
            <article
              class="group flex flex-col rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition duration-300 hover:-translate-y-1 hover:border-blue-200 hover:shadow-xl"
            >

              <!-- Categoría -->
              <div class="mb-5 flex items-center justify-between">

                <span
                  class="rounded-lg bg-blue-50 px-3 py-1.5 text-xs font-bold uppercase tracking-wide text-blue-800"
                >
                  {{ offering.category }}
                </span>

                <!-- Indicador -->
                <span
                  class="h-2.5 w-2.5 rounded-full bg-emerald-500"
                  title="Disponible"
                ></span>

              </div>

              <!-- Título -->
              <h3
                class="text-xl font-bold text-slate-900 transition group-hover:text-blue-800"
              >
                {{ offering.title }}
              </h3>

              <!-- Descripción -->
              <p
                class="mt-3 flex-1 text-sm leading-6 text-slate-500"
              >
                {{ offering.description }}
              </p>

              <!-- Separador -->
              <div class="my-5 border-t border-slate-100"></div>

              <!-- Precio -->
              <div class="flex items-end justify-between gap-4">

                <div>
                  <p class="text-xs font-medium text-slate-400">
                    Desde
                  </p>

                  <strong
                    class="mt-1 block text-xl font-bold text-slate-900"
                  >
                    {{
                      offering.price
                        | currency: "COP" : "symbol-narrow" : "1.0-0"
                    }}
                  </strong>
                </div>

                <!-- Botón visual -->
                <button
                  type="button"
                  class="cursor-pointer rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-900 focus:outline-none focus:ring-4 focus:ring-blue-900/20"
                >
                  Reservar
                </button>

              </div>

            </article>

          }

        </div>

        <!-- Estado cuando no hay servicios -->
        @if (!error && offerings.length === 0) {
          <div
            class="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center"
          >
            <h3 class="text-lg font-semibold text-slate-900">
              No hay servicios disponibles
            </h3>

            <p class="mt-2 text-sm text-slate-500">
              Actualmente no encontramos servicios en el catálogo.
            </p>
          </div>
        }

      </div>
    </section>
  `,
})
export class HomeComponent implements OnInit {
  offerings: Offering[] = [];
  error = "";

  constructor(private service: OfferingService) {}

  // Carga el catálogo de servicios cuando se inicializa el componente.
  ngOnInit(): void {
    this.service.list().subscribe({
      next: (r) => (this.offerings = r),
      error: () => (this.error = "No fue posible cargar el catálogo."),
    });
  }
}
