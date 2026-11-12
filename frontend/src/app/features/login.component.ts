import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 px-4 py-12 selection:bg-blue-500 selection:text-white">
      <!-- Glows decorativos de fondo -->
      <div class="pointer-events-none absolute -left-40 -top-40 h-[30rem] w-[30rem] rounded-full bg-blue-600/15 blur-[120px]" aria-hidden="true"></div>
      <div class="pointer-events-none absolute -bottom-40 -right-40 h-[30rem] w-[30rem] rounded-full bg-indigo-600/15 blur-[120px]" aria-hidden="true"></div>

      <section class="relative w-full max-w-md">
        <!-- Logo y Branding -->
        <div class="mb-8 text-center">
          <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-500 text-xl font-black text-white shadow-lg shadow-blue-500/25 ring-1 ring-white/20">
            S
          </div>
          <h1 class="mt-4 text-2xl font-extrabold tracking-tight text-white sm:text-3xl">
            SkillBridge
          </h1>
          <p class="mt-2 text-sm text-slate-400">
            {{ mode === 'login' ? 'Accede a tu panel de control' : 'Crea tu cuenta profesional en segundos' }}
          </p>
        </div>

        <!-- Tarjeta Principal -->
        <div class="rounded-3xl border border-white/10 bg-slate-900/80 p-6 shadow-2xl backdrop-blur-xl sm:p-8">
          
          <!-- Selector de Modo / Tabs -->
          <div class="mb-6 flex rounded-xl bg-slate-950/60 p-1 ring-1 ring-white/10">
            <button
              type="button"
              (click)="setMode('login')"
              [class]="mode === 'login' 
                ? 'bg-slate-800 text-white shadow-sm' 
                : 'text-slate-400 hover:text-slate-200'"
              class="cursor-pointer w-1/2 rounded-lg py-2 text-xs font-semibold transition-all duration-200"
            >
              Iniciar sesión
            </button>
            <button
              type="button"
              (click)="setMode('register')"
              [class]="mode === 'register' 
                ? 'bg-slate-800 text-white shadow-sm' 
                : 'text-slate-400 hover:text-slate-200'"
              class="cursor-pointer w-1/2 rounded-lg py-2 text-xs font-semibold transition-all duration-200"
            >
              Registrarse
            </button>
          </div>

          <!-- Banner de Error Intuitivo -->
          @if (error) {
            <div role="alert" class="mb-6 flex items-start gap-3 rounded-2xl border border-red-500/20 bg-red-500/10 p-4 text-xs font-medium text-red-300 animate-in fade-in duration-200">
              <svg class="h-5 w-5 shrink-0 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
              </svg>
              <div class="flex-1">
                <p class="font-semibold text-red-200">Atención</p>
                <p class="mt-0.5 text-red-300/90 leading-relaxed">{{ error }}</p>
              </div>
            </div>
          }

          <!-- Formulario Nativo (Soporta Submit con Tecla Enter) -->
          <form (ngSubmit)="submit()" class="space-y-4">
            
            <!-- Campo Nombre (Solo Registro) -->
            @if (mode === 'register') {
              <div>
                <label for="name" class="mb-1.5 block text-xs font-medium text-slate-300">
                  Nombre completo
                </label>
                <div class="relative">
                  <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
                    </svg>
                  </div>
                  <input
                    id="name"
                    type="text"
                    [(ngModel)]="name"
                    name="name"
                    placeholder="Ej. María García"
                    autocomplete="name"
                    class="w-full rounded-xl border border-white/10 bg-slate-950/50 py-3 pl-10 pr-4 text-sm text-white placeholder:text-slate-500 outline-none transition focus:border-blue-500 focus:bg-slate-950 focus:ring-2 focus:ring-blue-500/20"
                  />
                </div>
              </div>
            }

            <!-- Campo Correo -->
            <div>
              <label for="email" class="mb-1.5 block text-xs font-medium text-slate-300">
                Correo electrónico
              </label>
              <div class="relative">
                <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
                  </svg>
                </div>
                <input
                  id="email"
                  type="email"
                  [(ngModel)]="email"
                  name="email"
                  placeholder="nombre@ejemplo.com"
                  autocomplete="email"
                  class="w-full rounded-xl border border-white/10 bg-slate-950/50 py-3 pl-10 pr-4 text-sm text-white placeholder:text-slate-500 outline-none transition focus:border-blue-500 focus:bg-slate-950 focus:ring-2 focus:ring-blue-500/20"
                />
              </div>
            </div>

            <!-- Campo Contraseña con Toggle Ocultar/Mostrar -->
            <div>
              <div class="mb-1.5 flex items-center justify-between">
                <label for="password" class="block text-xs font-medium text-slate-300">
                  Contraseña
                </label>
                @if (mode === 'login') {
                  <a href="#" (click)="$event.preventDefault()" class="text-xs text-blue-400 hover:text-blue-300 transition-colors">
                    ¿Olvidaste tu contraseña?
                  </a>
                }
              </div>
              <div class="relative">
                <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                  </svg>
                </div>
                <input
                  id="password"
                  [type]="showPassword ? 'text' : 'password'"
                  [(ngModel)]="password"
                  name="password"
                  placeholder="••••••••"
                  [autocomplete]="mode === 'login' ? 'current-password' : 'new-password'"
                  class="w-full rounded-xl border border-white/10 bg-slate-950/50 py-3 pl-10 pr-11 text-sm text-white placeholder:text-slate-500 outline-none transition focus:border-blue-500 focus:bg-slate-950 focus:ring-2 focus:ring-blue-500/20"
                />
                <button
                  type="button"
                  (click)="showPassword = !showPassword"
                  class="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-slate-200"
                  tabindex="-1"
                >
                  @if (showPassword) {
                    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858-5.908a8.959 8.959 0 013.68-.763c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m-4.692-4.692a3 3 0 00-4.243-4.243m4.243 4.243L3 3l18 18"/>
                    </svg>
                  } @else {
                    <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                    </svg>
                  }
                </button>
              </div>
              @if (mode === 'register') {
                <p class="mt-1.5 text-[11px] text-slate-500">Mínimo 6 caracteres.</p>
              }
            </div>

            <!-- Botón Principal con Spinner de Carga -->
            <button
              type="submit"
              [disabled]="loading"
              class="cursor-pointer mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3.5 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 transition duration-200 hover:bg-blue-500 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60"
            >
              @if (loading) {
                <svg class="h-4 w-4 animate-spin text-white" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>Procesando...</span>
              } @else {
                <span>{{ mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta gratis' }}</span>
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"/>
                </svg>
              }
            </button>
          </form>
        </div>

        <!-- Pie de página de Términos -->
        <p class="mt-6 text-center text-xs leading-5 text-slate-500">
          Al continuar, aceptas los
          <a href="#" (click)="$event.preventDefault()" class="text-slate-400 underline hover:text-slate-300">Términos de servicio</a>
          y la
          <a href="#" (click)="$event.preventDefault()" class="text-slate-400 underline hover:text-slate-300">Política de privacidad</a>.
        </p>
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