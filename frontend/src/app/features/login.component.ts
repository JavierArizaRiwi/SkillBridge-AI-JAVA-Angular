import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="grid min-h-screen bg-[#FBFBFA] lg:grid-cols-2">

      <!-- Panel de marca (solo escritorio) -->
      <aside class="hidden flex-col justify-end bg-[#111111] p-12 lg:flex">
        <div class="mb-28">
          <p class="font-display max-w-md text-5xl text-[#FFFFFF]">
            Mentorías que conectan tu experiencia con tu siguiente paso.
          </p>
          <p class="mt-6 max-w-sm text-base leading-7 text-[#FFFFFF]">
            Reserva sesiones con mentores y recibe recomendaciones según tu objetivo.
          </p>
        </div>
      </aside>

      <!-- Formulario -->
      <section class="flex items-center justify-center px-4 py-12 sm:px-8">
        <div class="enter-rise w-full max-w-sm">

          <!-- Marca (móvil) -->
          <div class="mb-10 flex items-center gap-2.5 lg:hidden">
            <svg class="h-8 w-8 shrink-0" viewBox="0 0 32 32" fill="none" aria-hidden="true">
              <rect width="32" height="32" rx="8" fill="#111111"/>
              <path d="M8 22a8 8 0 0 1 16 0" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"/>
              <path d="M5.5 22h21" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"/>
              <circle cx="16" cy="9" r="2.3" fill="#FFFFFF"/>
            </svg>
            <span class="font-display text-2xl text-[#111111]">SkillBridge<span class="text-[#787774]"> AI</span></span>
          </div>

          <h1 class="font-display text-4xl text-[#111111] sm:text-5xl">
            {{ mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta' }}
          </h1>
          <p class="mt-3 text-base text-[#787774]">
            {{ mode === 'login' ? 'Accede a tu panel de control' : 'Crea tu cuenta profesional en segundos' }}
          </p>

          <!-- Selector de modo -->
          <div class="mt-8 flex gap-6 border-b border-[#EAEAEA]">
            <button
              type="button"
              (click)="setMode('login')"
              [class]="mode === 'login' ? 'border-[#111111] text-[#111111]' : 'border-transparent text-[#787774] hover:text-[#111111]'"
              class="-mb-px cursor-pointer border-b-2 py-3 text-sm font-medium transition-colors duration-150"
            >
              Iniciar sesión
            </button>
            <button
              type="button"
              (click)="setMode('register')"
              [class]="mode === 'register' ? 'border-[#111111] text-[#111111]' : 'border-transparent text-[#787774] hover:text-[#111111]'"
              class="-mb-px cursor-pointer border-b-2 py-3 text-sm font-medium transition-colors duration-150"
            >
              Registrarse
            </button>
          </div>

          @if (error) {
            <div role="alert" class="enter-rise mt-6 rounded-lg bg-[#FDEBEC] px-4 py-3 text-sm text-[#9F2F2D]">
              <p class="font-medium">Atención</p>
              <p class="mt-0.5 leading-relaxed">{{ error }}</p>
            </div>
          }

          <form (ngSubmit)="submit()" class="mt-8 space-y-5">

            @if (mode === 'register') {
              <div class="enter-rise">
                <label for="name" class="mb-2 block text-sm text-[#787774]">Nombre completo</label>
                <input
                  id="name"
                  type="text"
                  [(ngModel)]="name"
                  name="name"
                  placeholder="Ej. María García"
                  autocomplete="name"
                  class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] hover:border-[#C9C8C4] focus:border-[#111111]"
                />
              </div>
            }

            <div>
              <label for="email" class="mb-2 block text-sm text-[#787774]">Correo electrónico</label>
              <input
                id="email"
                type="email"
                [(ngModel)]="email"
                name="email"
                placeholder="nombre@ejemplo.com"
                autocomplete="email"
                class="w-full rounded-md border border-[#EAEAEA] bg-white px-3 py-2.5 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] hover:border-[#C9C8C4] focus:border-[#111111]"
              />
            </div>

            <div>
              <div class="mb-2 flex items-center justify-between">
                <label for="password" class="block text-sm text-[#787774]">Contraseña</label>
                @if (mode === 'login') {
                  <a href="#" (click)="$event.preventDefault()" class="text-sm text-[#787774] transition-colors duration-150 hover:text-[#111111]">
                    ¿Olvidaste tu contraseña?
                  </a>
                }
              </div>
              <div class="relative">
                <input
                  id="password"
                  [type]="showPassword ? 'text' : 'password'"
                  [(ngModel)]="password"
                  name="password"
                  placeholder="••••••••"
                  [autocomplete]="mode === 'login' ? 'current-password' : 'new-password'"
                  class="w-full rounded-md border border-[#EAEAEA] bg-white py-2.5 pl-3 pr-11 text-sm text-[#111111] outline-none transition-colors duration-150 placeholder:text-[#9B9A97] hover:border-[#C9C8C4] focus:border-[#111111]"
                />
                <button
                  type="button"
                  (click)="showPassword = !showPassword"
                  class="absolute inset-y-0 right-0 flex cursor-pointer items-center pr-3 text-[#787774] transition-colors duration-150 hover:text-[#111111]"
                  tabindex="-1"
                  [attr.aria-label]="showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'"
                >
                  @if (showPassword) {
                    <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                      <path d="M53.92 34.62a8 8 0 1 0-11.84 10.76l19.24 21.17C25 88.84 9.38 123.2 8.69 124.76a8 8 0 0 0 0 6.5c.35.79 8.82 19.57 27.65 38.4C61.43 194.74 93.12 208 128 208a127.11 127.11 0 0 0 52.07-10.83l22 24.21a8 8 0 1 0 11.84-10.76Zm47.33 75.84 41.67 45.85a32 32 0 0 1-41.67-45.85ZM128 192c-30.78 0-57.67-11.19-79.93-33.25A133.47 133.47 0 0 1 25 128c4.69-8.79 19.66-33.39 47.35-49.38l18 19.75a48 48 0 0 0 63.66 70l14.73 16.2A112 112 0 0 1 128 192Zm6-95.43a8 8 0 0 1 3-15.72 48.16 48.16 0 0 1 38.77 42.64 8 8 0 0 1-7.22 8.71 6.39 6.39 0 0 1-.75 0 8 8 0 0 1-8-7.26A32.2 32.2 0 0 0 134 96.57Zm113.28 34.69c-.42.94-10.55 23.37-33.36 43.8a8 8 0 1 1-10.67-11.92A132.77 132.77 0 0 0 231.05 128a133.15 133.15 0 0 0-23.12-30.77C185.67 75.19 158.78 64 128 64a118.37 118.37 0 0 0-19.36 1.57A8 8 0 1 1 106 49.79 134 134 0 0 1 128 48c34.88 0 66.57 13.26 91.66 38.35 18.83 18.83 27.3 37.62 27.65 38.41a8 8 0 0 1 0 6.5Z"/>
                    </svg>
                  } @else {
                    <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
                      <path d="M247.31 124.76c-.35-.79-8.82-19.58-27.65-38.41C194.57 61.26 162.88 48 128 48S61.43 61.26 36.34 86.35C17.51 105.18 9 124 8.69 124.76a8 8 0 0 0 0 6.5c.35.79 8.82 19.57 27.65 38.4C61.43 194.74 93.12 208 128 208s66.57-13.26 91.66-38.34c18.83-18.83 27.3-37.61 27.65-38.4a8 8 0 0 0 0-6.5ZM128 192c-30.78 0-57.67-11.19-79.93-33.25A133.47 133.47 0 0 1 25 128a133.33 133.33 0 0 1 23.07-30.75C70.33 75.19 97.22 64 128 64s57.67 11.19 79.93 33.25A133.46 133.46 0 0 1 231.05 128c-7.21 13.46-38.62 64-103.05 64Zm0-112a48 48 0 1 0 48 48 48.05 48.05 0 0 0-48-48Zm0 80a32 32 0 1 1 32-32 32 32 0 0 1-32 32Z"/>
                    </svg>
                  }
                </button>
              </div>
              @if (mode === 'register') {
                <p class="mt-2 text-xs text-[#787774]">Mínimo 6 caracteres.</p>
              }
            </div>

            <button
              type="submit"
              [disabled]="loading"
              class="mt-2 flex w-full cursor-pointer items-center justify-center gap-2 rounded-md bg-[#111111] px-4 py-3 text-sm font-medium text-white transition-[transform,background-color,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] disabled:pointer-events-none disabled:opacity-50 motion-reduce:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#111111]"
            >
              @if (loading) {
                <svg class="h-4 w-4 animate-spin [animation-duration:600ms]" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>Procesando...</span>
              } @else {
                <span>{{ mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta gratis' }}</span>
              }
            </button>
          </form>

          <p class="mt-8 text-xs leading-5 text-[#787774]">
            Al continuar, aceptas los
            <a href="#" (click)="$event.preventDefault()" class="underline underline-offset-2 transition-colors duration-150 hover:text-[#111111]">Términos de servicio</a>
            y la
            <a href="#" (click)="$event.preventDefault()" class="underline underline-offset-2 transition-colors duration-150 hover:text-[#111111]">Política de privacidad</a>.
          </p>
        </div>
      </section>
    </main>
  `,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  mode: 'login' | 'register' = 'login';
  name = '';
  email = '';
  password = '';

  error = '';
  loading = false;
  showPassword = false;

  setMode(newMode: 'login' | 'register') {
    if (this.mode !== newMode) {
      this.mode = newMode;
      this.error = '';
    }
  }

  submit(): void {
    // Evita doble envío si Enter/click llega antes de que el botón se deshabilite visualmente
    if (this.loading) return;

    this.error = '';

    // 1. Pre-validaciones inmediatas en cliente (Evita peticiones innecesarias)
    if (!this.email.trim() || !this.password.trim()) {
      this.error = 'Por favor, completa todos los campos obligatorios.';
      return;
    }

    if (this.mode === 'register' && !this.name.trim()) {
      this.error = 'Escribe tu nombre para crear la cuenta.';
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(this.email.trim())) {
      this.error = 'Introduce un correo electrónico válido.';
      return;
    }

    if (this.password.length < 6) {
      this.error = 'La contraseña debe tener al menos 6 caracteres.';
      return;
    }

    // 2. Envío de petición al backend
    this.loading = true;
    const request =
      this.mode === 'login'
        ? this.auth.login(this.email.trim(), this.password)
        : this.auth.register(this.name.trim(), this.email.trim(), this.password);

    request.subscribe({
      next: () => {
        this.loading = false;
        this.router.navigateByUrl('/ai');
      },
      error: (e) => {
        this.loading = false;
        this.error =
          e?.error?.detail ||
          (this.mode === 'login'
            ? 'Credenciales incorrectas. Verifica tu correo y contraseña.'
            : 'No fue posible completar el registro. Inténtalo de nuevo.');
      },
    });
  }
}