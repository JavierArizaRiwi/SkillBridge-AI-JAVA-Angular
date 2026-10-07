import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <!-- Página completa de autenticación -->
    <main class="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 px-4 py-12">

      <!-- Elementos decorativos del fondo -->
      <div
        class="absolute -left-32 -top-32 h-80 w-80 rounded-full bg-blue-600/20 blur-3xl"
        aria-hidden="true"
      ></div>

      <div
        class="absolute -bottom-32 -right-32 h-80 w-80 rounded-full bg-indigo-600/20 blur-3xl"
        aria-hidden="true"
      ></div>

      <!-- Contenedor del formulario -->
      <section class="relative w-full max-w-md">

        <!-- Marca -->
        <div class="mb-8 text-center">

          <div
            class="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600 text-xl font-bold text-white shadow-lg shadow-blue-600/20"
          >
            S
          </div>

          <h1 class="mt-5 text-2xl font-bold tracking-tight text-white">
            SkillBridge
          </h1>

          <p class="mt-2 text-sm text-slate-400">
            Tu camino hacia nuevas oportunidades
          </p>

        </div>

        <!-- Tarjeta -->
        <div
          class="rounded-2xl border border-white/10 bg-white p-6 shadow-2xl sm:p-8"
        >

          <!-- Encabezado -->
          <div class="mb-7">

            <h2 class="text-2xl font-bold text-slate-900">
              {{ mode === 'login' ? 'Bienvenido de nuevo' : 'Crear una cuenta' }}
            </h2>

            <p class="mt-2 text-sm leading-6 text-slate-500">
              {{
                mode === 'login'
                  ? 'Ingresa tus datos para continuar.'
                  : 'Completa tus datos para comenzar.'
              }}
            </p>

          </div>

          <!-- Formulario -->
          <div class="space-y-5">

            <!-- Nombre: solamente durante registro -->
            @if (mode === 'register') {
              <div>
                <label
                  for="name"
                  class="mb-2 block text-sm font-semibold text-slate-700"
                >
                  Nombre
                </label>

                <input
                  id="name"
                  type="text"
                  [(ngModel)]="name"
                  name="name"
                  placeholder="Tu nombre"
                  autocomplete="name"
                  class="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 hover:border-slate-400 focus:border-blue-700 focus:ring-4 focus:ring-blue-700/10"
                />
              </div>
            }

            <!-- Correo -->
            <div>
              <label
                for="email"
                class="mb-2 block text-sm font-semibold text-slate-700"
              >
                Correo electrónico
              </label>

              <input
                id="email"
                type="email"
                [(ngModel)]="email"
                name="email"
                placeholder="nombre@ejemplo.com"
                autocomplete="email"
                class="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 hover:border-slate-400 focus:border-blue-700 focus:ring-4 focus:ring-blue-700/10"
              />
            </div>

            <!-- Contraseña -->
            <div>
              <label
                for="password"
                class="mb-2 block text-sm font-semibold text-slate-700"
              >
                Contraseña
              </label>

              <input
                id="password"
                type="password"
                [(ngModel)]="password"
                name="password"
                placeholder="••••••••"
                autocomplete="current-password"
                class="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 hover:border-slate-400 focus:border-blue-700 focus:ring-4 focus:ring-blue-700/10"
              />
            </div>

            <!-- Error -->
            @if (error) {
              <div
                role="alert"
                class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-5 text-red-700"
              >
                {{ error }}
              </div>
            }

            <!-- Botón principal -->
            <button
              type="button"
              (click)="submit()"
              class="cursor-pointer w-full rounded-xl bg-slate-950 px-4 py-3.5 text-sm font-semibold text-white shadow-sm transition duration-200 hover:bg-blue-900 focus:outline-none focus:ring-4 focus:ring-blue-900/20"
            >
              {{ mode === 'login' ? 'Ingresar' : 'Crear cuenta' }}
            </button>

            <!-- Separador -->
            <div class="relative py-1">
              <div class="absolute inset-0 flex items-center">
                <div class="w-full border-t border-slate-200"></div>
              </div>

              <div class="relative flex justify-center">
                <span class="bg-white px-3 text-xs text-slate-400">
                  o
                </span>
              </div>
            </div>

            <!-- Cambiar entre login y registro -->
            <button
              type="button"
              (click)="toggle()"
              class="cursor-pointer w-full rounded-xl border border-slate-300 bg-white px-4 py-3.5 text-sm font-semibold text-slate-700 transition duration-200 hover:border-slate-400 hover:bg-slate-50 focus:outline-none focus:ring-4 focus:ring-slate-200"
            >
              {{
                mode === 'login'
                  ? 'Crear una cuenta'
                  : 'Ya tengo una cuenta'
              }}
            </button>

          </div>
        </div>

        <!-- Texto inferior -->
        <p class="mt-6 text-center text-xs leading-5 text-slate-500">
          Al continuar, aceptas los términos y condiciones
          de la plataforma.
        </p>

      </section>
    </main>
  `,
})
export class LoginComponent {
  mode: 'login' | 'register' = 'login';
  name = '';
  email = '';
  password = '';
  error = '';

  constructor(
    private auth: AuthService,
    private router: Router
  ) {}

  // Alterna entre el formulario de inicio de sesión y registro.
  toggle() {
    this.mode = this.mode === 'login' ? 'register' : 'login';
    this.error = '';
  }

  // Envía las credenciales al backend según el modo actual.
  submit() {
    this.error = '';

    const request =
      this.mode === 'login'
        ? this.auth.login(this.email, this.password)
        : this.auth.register(this.name, this.email, this.password);

    request.subscribe({
      next: () => this.router.navigateByUrl('/ai'),
      error: (e) =>
        (this.error =
          e?.error?.detail || 'No fue posible autenticar.'),
    });
  }
}