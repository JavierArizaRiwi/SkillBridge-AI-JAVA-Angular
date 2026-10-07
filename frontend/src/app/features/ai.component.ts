
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { apiBase } from '../core/api';

@Component({
  selector: 'app-ai',
  standalone: true,
  imports: [FormsModule],
  template: `
    <!-- Contenedor principal de la sección -->
    <section class="min-h-[calc(100vh-4rem)] bg-slate-50 px-4 py-12 sm:px-6 lg:py-20">

      <!-- Limita el ancho y centra el contenido -->
      <div class="mx-auto max-w-3xl">

        <!-- Encabezado de la sección -->
        <div class="mb-8 text-center">
          <span class="mb-4 inline-flex items-center rounded-full border border-blue-100 bg-blue-50 px-4 py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-blue-800">
            Java + Gemini
          </span>

          <h1 class="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Asistente de recomendaciones
          </h1>

          <p class="mx-auto mt-4 max-w-2xl text-sm leading-7 text-slate-500 sm:text-base">
            Obtén recomendaciones personalizadas para alcanzar tus objetivos
            profesionales y mejorar tus habilidades técnicas.
          </p>
        </div>

        <!-- Tarjeta principal -->
        <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-[0_12px_40px_rgba(15,23,42,0.06)] sm:p-8">

          <!-- Nota informativa sobre la seguridad -->
          <div class="mb-8 flex gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div class="mt-0.5 shrink-0 text-blue-800">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="10"/>
                <path d="M12 16v-4"/>
                <path d="M12 8h.01"/>
              </svg>
            </div>

            <p class="text-sm leading-6 text-slate-600">
              Tu API key está protegida en Spring Boot.
              Angular se comunica exclusivamente con tu backend,
              sin exponer credenciales de Gemini.
            </p>
          </div>

          <!-- Formulario para introducir el objetivo -->
          <div class="space-y-3">
            <label
              for="goal"
              class="block text-sm font-semibold text-slate-800"
            >
              ¿Cuál es tu objetivo?
            </label>

            <textarea
              id="goal"
              [(ngModel)]="goal"
              rows="5"
              placeholder="Ej: Quiero prepararme para una entrevista backend Java..."
              class="w-full resize-y rounded-xl border border-slate-300 bg-white px-4 py-3.5 text-sm leading-6 text-slate-800 outline-none transition duration-200 placeholder:text-slate-400 hover:border-slate-400 focus:border-blue-800 focus:ring-4 focus:ring-blue-800/10"
            ></textarea>

            <p class="text-xs leading-5 text-slate-400">
              Describe lo que quieres aprender o conseguir para recibir
              recomendaciones más precisas.
            </p>
          </div>

          <!-- Botón de envío -->
          <div class="mt-6">
            <button
              type="button"
              [disabled]="loading"
              (click)="ask()"
              class="cursor-pointer inline-flex w-full items-center justify-center gap-2 rounded-xl bg-slate-900 px-6 py-3.5 text-sm font-semibold text-white shadow-sm transition duration-200 hover:bg-blue-900 focus:outline-none focus:ring-4 focus:ring-blue-900/20 disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
            >
              @if (loading) {
                <!-- Indicador visual de carga -->
                <svg
                  class="h-4 w-4 animate-spin"
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <circle
                    class="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    stroke-width="4"
                  />
                  <path
                    class="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"
                  />
                </svg>
                Generando recomendación...
              } @else {
                Pedir recomendación
              }
            </button>
          </div>

          <!-- Mensaje de error -->
          @if (error) {
            <div
              role="alert"
              class="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-700"
            >
              {{ error }}
            </div>
          }

          <!-- Respuesta generada por Gemini -->
          @if (answer) {
            <div
              aria-live="polite"
              class="mt-8 border-t border-slate-200 pt-6"
            >
              <div class="mb-4 flex items-center gap-3">
                <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-50 text-blue-900">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    aria-hidden="true"
                  >
                    <path d="m12 3 1.9 5.8L20 11l-6.1 2.2L12 19l-1.9-5.8L4 11l6.1-2.2L12 3Z"/>
                    <path d="m19 14 1.2 2.8L23 18l-2.8 1.2L19 22l-1.2-2.8L15 18l2.8-1.2L19 14Z"/>
                  </svg>
                </div>

                <h2 class="text-lg font-bold text-slate-900">
                  Tu recomendación
                </h2>
              </div>

              <div class="whitespace-pre-line text-sm leading-7 text-slate-600">
                {{ answer }}
              </div>
            </div>
          }
        </div>

        <!-- Texto complementario -->
        <p class="mt-6 text-center text-xs leading-5 text-slate-400">
          Las recomendaciones generadas por IA son orientativas.
          Revisa la información según tus necesidades.
        </p>
      </div>
    </section>
  `
})
export class AiComponent {
  goal = '';
  answer = '';
  error = '';
  loading = false;

  constructor(private http: HttpClient) {}

  // Envía el objetivo al backend y gestiona la respuesta.
  ask() {
    this.error = '';
    this.answer = '';
    this.loading = true;

    this.http
      .post<{ recommendation: string }>(
        `${apiBase()}/ai/recommendations`,
        { goal: this.goal }
      )
      .subscribe({
        next: (r) => {
          this.answer = r.recommendation;
          this.loading = false;
        },
        error: (e) => {
          this.error =
            e?.error?.detail ||
            'Inicia sesión y verifica GEMINI_API_KEY.';
          this.loading = false;
        }
      });
  }
}
