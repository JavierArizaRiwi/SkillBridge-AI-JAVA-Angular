import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">

        <!-- Logo / Marca -->
        <a
          routerLink="/"
          (click)="closeMenu()"
          class="flex items-center gap-2.5 transition-opacity hover:opacity-80"
        >
          <span
            class="flex h-9 w-9 items-center justify-center rounded-xl
                   bg-slate-900 text-sm font-bold text-white shadow-sm"
          >
            S
          </span>

          <span class="text-lg font-bold tracking-tight text-slate-900">
            SkillBridge
            <span class="text-blue-600">AI</span>
          </span>
        </a>

        <!-- Navegación Escritorio -->
        <nav class="hidden items-center gap-1 md:flex">

          <a
            routerLink="/"
            routerLinkActive="bg-slate-100 text-slate-900"
            [routerLinkActiveOptions]="{ exact: true }"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                  transition hover:bg-slate-50 hover:text-slate-900"
          >
            Servicios
          </a>

          <a
            routerLink="/book"
            routerLinkActive="bg-slate-100 text-slate-900"
            class="cursor-pointer rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                   transition hover:bg-slate-50 hover:text-slate-900"
          >
            Reservar
          </a>

          <a
            routerLink="/ai"
            routerLinkActive="bg-slate-100 text-slate-900"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                   transition hover:bg-slate-50 hover:text-slate-900"
          >
            IA
          </a>

          <a
            routerLink="/bookings/me"
            routerLinkActive="bg-slate-100 text-slate-900"
            class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                   transition hover:bg-slate-50 hover:text-slate-900"
          >
            Mis reservas
          </a>

          @if (auth.hasRole('PROVIDER', 'ADMIN')) {
            <a
              routerLink="/provider/offerings"
              routerLinkActive="bg-slate-100 text-slate-900"
              class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600
                     transition hover:bg-slate-50 hover:text-slate-900"
            >
              {{ auth.hasRole('ADMIN') ? 'Gestionar servicios' : 'Mis servicios' }}
            </a>
          }

          <!-- Usuario no autenticado -->
          @if (!auth.isAuthenticated()) {

            <a
              routerLink="/login"
              class="ml-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm
                    font-semibold text-slate-700 shadow-sm transition
                    hover:bg-slate-900 hover:text-white"
            >
              Ingresar
            </a>

          } @else {

            <!-- Usuario autenticado -->
            <button
              type="button"
              (click)="auth.logout()"
              class="cursor-pointer ml-2 rounded-xl border border-slate-200 bg-white px-4 py-2
                     text-sm font-semibold text-slate-700 transition
                     hover:border-slate-300 hover:bg-slate-50"
            >
              Salir
            </button>

          }

        </nav>

        <!-- Botón Menú Hamburguesa (Móvil) -->
        <button
          type="button"
          (click)="toggleMenu()"
          class="rounded-lg p-2 text-slate-600 transition
                 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-slate-300 md:hidden"
          [attr.aria-expanded]="isMenuOpen"
          aria-label="Abrir menú"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            class="h-6 w-6"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            stroke-width="1.8"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              d="M4 6h16M4 12h16M4 18h16"
            />
          </svg>
        </button>

      </div>
    </header>

    <!-- Overlay / Fondo Oscuro Suave -->
    <div
      (click)="closeMenu()"
      class="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm transition-opacity duration-300 md:hidden"
      [class.opacity-100]="isMenuOpen"
      [class.opacity-0]="!isMenuOpen"
      [class.pointer-events-auto]="isMenuOpen"
      [class.pointer-events-none]="!isMenuOpen"
    ></div>

    <!-- Menú Desplegable Lateral (Drawer de derecha a izquierda) -->
    <aside
      class="fixed top-0 right-0 z-50 h-full w-80 max-w-[75vw] bg-white p-6 shadow-2xl transition-transform duration-300 ease-in-out md:hidden flex flex-col justify-between"
      [class.translate-x-0]="isMenuOpen"
      [class.translate-x-full]="!isMenuOpen"
    >
      <div>
        <!-- Encabezado del Menú Móvil -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-4 mb-6">
          <div class="flex items-center gap-2">
            <span
              class="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-900 text-xs font-bold text-white"
            >
              S
            </span>
            <span class="text-base font-bold tracking-tight text-slate-900">
              SkillBridge <span class="text-blue-600">AI</span>
            </span>
          </div>

          <!-- Botón Cierre (X) -->
          <button
            type="button"
            (click)="closeMenu()"
            class="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
            aria-label="Cerrar menú"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              class="h-5 w-5"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              stroke-width="2"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Enlaces de Navegación -->
        <nav class="flex flex-col space-y-2">
          <a
            routerLink="/"
            routerLinkActive="bg-slate-100 text-slate-900 font-semibold"
            [routerLinkActiveOptions]="{ exact: true }"
            (click)="closeMenu()"
            class="rounded-xl px-4 py-3 text-base font-medium text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
          >
            Servicios
          </a>

          <a
            routerLink="/book"
            routerLinkActive="bg-slate-100 text-slate-900 font-semibold"
            (click)="closeMenu()"
            class="rounded-xl px-4 py-3 text-base font-medium text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
          >
            Reservar
          </a>

          <a
            routerLink="/ai"
            routerLinkActive="bg-slate-100 text-slate-900 font-semibold"
            (click)="closeMenu()"
            class="rounded-xl px-4 py-3 text-base font-medium text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
          >
            IA
          </a>

          <a
            routerLink="/bookings/me"
            routerLinkActive="bg-slate-100 text-slate-900 font-semibold"
            (click)="closeMenu()"
            class="rounded-xl px-4 py-3 text-base font-medium text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
          >
            Mis reservas
          </a>

          @if (auth.hasRole('PROVIDER', 'ADMIN')) {
            <a
              routerLink="/provider/offerings"
              routerLinkActive="bg-slate-100 text-slate-900 font-semibold"
              (click)="closeMenu()"
              class="rounded-xl px-4 py-3 text-base font-medium text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
            >
              {{ auth.hasRole('ADMIN') ? 'Gestionar servicios' : 'Mis servicios' }}
            </a>
          }
        </nav>
      </div>

      <!-- Pie del Menú (Autenticación) -->
      <div class="border-t border-slate-100 pt-4 mt-auto">
        @if (!auth.isAuthenticated()) {
          <a
            routerLink="/login"
            (click)="closeMenu()"
            class="block w-full text-center rounded-xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800"
          >
            Ingresar
          </a>
        } @else {
          <button
            type="button"
            (click)="handleLogout()"
            class="w-full text-center rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
          >
            Cerrar sesión
          </button>
        }
      </div>
    </aside>

    <!-- Contenido de cada ruta -->
    <main>
      <router-outlet />
    </main>
  `,
})
export class AppComponent {
  auth = inject(AuthService);
  isMenuOpen = false;

  toggleMenu(): void {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu(): void {
    this.isMenuOpen = false;
  }

  handleLogout(): void {
    this.closeMenu();
    this.auth.logout();
  }
}
