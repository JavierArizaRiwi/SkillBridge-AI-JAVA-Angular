import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="sticky top-0 z-30 border-b border-[#EAEAEA] bg-[#FBFBFA]/85 backdrop-blur-md">
      <div class="mx-auto flex h-16 max-w-8xl items-center justify-between px-4 sm:px-6">

        <a routerLink="/" (click)="closeMenu()" aria-label="SkillBridge AI, inicio"
           class="flex items-center gap-2.5 transition-opacity duration-150 hover:opacity-70">
          <!-- Marca: un puente en arco; el punto es la persona que cruza (mentoría) -->
          <svg class="h-8 w-8 shrink-0" viewBox="0 0 32 32" fill="none" aria-hidden="true">
            <rect width="32" height="32" rx="8" fill="#111111"/>
            <path d="M8 22a8 8 0 0 1 16 0" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"/>
            <path d="M5.5 22h21" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"/>
            <circle cx="16" cy="9" r="2.3" fill="#FFFFFF"/>
          </svg>
          <span class="font-display text-2xl text-[#111111]">SkillBridge<span class="text-[#787774]"> AI</span></span>
        </a>

        <!-- Navegación escritorio -->
        <div class="hidden items-center gap-4 lg:gap-6 md:flex">
          <nav class="flex items-center gap-1
                      [&>a]:rounded-md [&>a]:px-3 [&>a]:py-2 [&>a]:text-sm [&>a]:font-medium [&>a]:text-[#787774]
                      [&>a]:transition-colors [&>a]:duration-150 [&>a:hover]:bg-[#F1F0ED] [&>a:hover]:text-[#111111]">
            <a routerLink="/" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" [routerLinkActiveOptions]="{ exact: true }">Servicios</a>
            <a routerLink="/book" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]">Reservar</a>
            <a routerLink="/ai" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]">IA</a>
            <a routerLink="/trending" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]">Tendencias</a>
            <a routerLink="/bookings/me" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]">Mis reservas</a>
            <a routerLink="/mentorships/me" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]">Mis mentorías</a>

            @if (auth.hasRole('PROVIDER', 'ADMIN')) {
              <a routerLink="/provider/offerings" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]">
                {{ auth.hasRole('ADMIN') ? 'Gestionar servicios' : 'Mis servicios' }}
              </a>
            }

            @if (auth.hasRole('ADMIN')) {
              <a routerLink="/admin/users" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]">Usuarios</a>
            }
          </nav>

          <div class="flex items-center gap-3 border-l border-[#EAEAEA] pl-4 lg:pl-6">
            @if (!auth.isAuthenticated()) {
              <a routerLink="/login"
                 class="rounded-md bg-[#111111] px-4 py-2 text-sm font-medium text-white transition-[background-color,transform] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-[#333333] [@media(hover:hover)]:hover:scale-[1.01] active:scale-[0.98] motion-reduce:active:scale-100">
                Ingresar
              </a>
            } @else {
              <div class="hidden max-w-44 text-right xl:block">
                <p class="truncate text-xs font-medium text-[#111111]">{{ auth.email() }}</p>
                <p class="text-[11px] uppercase tracking-wide text-[#787774]">{{ roleLabel() }}</p>
              </div>
              <button type="button" (click)="auth.logout()"
                      class="cursor-pointer rounded-md px-3 py-2 text-sm font-medium text-[#111111] transition-[background-color,transform] duration-150 hover:bg-[#F1F0ED] active:scale-[0.97] motion-reduce:active:scale-100">
                Salir
              </button>
            }
          </div>
        </div>

        <!-- Hamburguesa (móvil) -->
        <button type="button" (click)="toggleMenu()" aria-label="Abrir menú" [attr.aria-expanded]="isMenuOpen"
                class="cursor-pointer rounded-md p-2 text-[#111111] transition-[background-color,transform] duration-150 hover:bg-[#F1F0ED] active:scale-[0.94] motion-reduce:active:scale-100 md:hidden">
          <svg class="h-5 w-5" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
            <path d="M224 128a8 8 0 0 1-8 8H40a8 8 0 0 1 0-16h176a8 8 0 0 1 8 8ZM40 72h176a8 8 0 0 0 0-16H40a8 8 0 0 0 0 16Zm176 112H40a8 8 0 0 0 0 16h176a8 8 0 0 0 0-16Z"/>
          </svg>
        </button>
      </div>
    </header>

    <!-- Scrim -->
    <div (click)="closeMenu()"
         class="fixed inset-0 z-40 bg-[#111111]/20 transition-opacity duration-300 ease-out md:hidden"
         [class.opacity-100]="isMenuOpen" [class.opacity-0]="!isMenuOpen"
         [class.pointer-events-auto]="isMenuOpen" [class.pointer-events-none]="!isMenuOpen"></div>

    <!-- Drawer móvil -->
    <aside class="fixed inset-y-0 right-0 z-50 flex w-72 max-w-[80vw] flex-col justify-between border-l border-[#EAEAEA] bg-[#FBFBFA] p-6 transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] motion-reduce:transition-none md:hidden"
           [class.translate-x-0]="isMenuOpen" [class.translate-x-full]="!isMenuOpen">
      <div class="overflow-y-auto">
        <div class="mb-8 flex items-center justify-between">
          <span class="flex items-center gap-2.5">
            <svg class="h-7 w-7 shrink-0" viewBox="0 0 32 32" fill="none" aria-hidden="true">
              <rect width="32" height="32" rx="8" fill="#111111"/>
              <path d="M8 22a8 8 0 0 1 16 0" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"/>
              <path d="M5.5 22h21" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"/>
              <circle cx="16" cy="9" r="2.3" fill="#FFFFFF"/>
            </svg>
            <span class="font-display text-xl text-[#111111]">SkillBridge<span class="text-[#787774]"> AI</span></span>
          </span>
          <button type="button" (click)="closeMenu()" aria-label="Cerrar menú"
                  class="cursor-pointer rounded-md p-2 text-[#787774] transition-[background-color,color,transform] duration-150 hover:bg-[#F1F0ED] hover:text-[#111111] active:scale-[0.94] motion-reduce:active:scale-100">
            <svg class="h-4 w-4" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
              <path d="M205.66 194.34a8 8 0 0 1-11.32 11.32L128 139.31l-66.34 66.35a8 8 0 0 1-11.32-11.32L116.69 128 50.34 61.66a8 8 0 0 1 11.32-11.32L128 116.69l66.34-66.35a8 8 0 0 1 11.32 11.32L139.31 128Z"/>
            </svg>
          </button>
        </div>

        <nav class="flex flex-col gap-1
                    [&>a]:rounded-md [&>a]:px-3 [&>a]:py-3 [&>a]:text-lg [&>a]:font-medium [&>a]:text-[#787774]
                    [&>a]:transition-[background-color,color,transform] [&>a]:duration-150 [&>a:hover]:bg-[#F1F0ED] [&>a:hover]:text-[#111111] [&>a:active]:scale-[0.98]">
          <a routerLink="/" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" [routerLinkActiveOptions]="{ exact: true }" (click)="closeMenu()">Servicios</a>
          <a routerLink="/book" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" (click)="closeMenu()">Reservar</a>
          <a routerLink="/ai" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" (click)="closeMenu()">IA</a>
          <a routerLink="/trending" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" (click)="closeMenu()">Tendencias</a>
          <a routerLink="/bookings/me" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" (click)="closeMenu()">Mis reservas</a>
          <a routerLink="/mentorships/me" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" (click)="closeMenu()">Mis mentorías</a>

          @if (auth.hasRole('PROVIDER', 'ADMIN')) {
            <a routerLink="/provider/offerings" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" (click)="closeMenu()">
              {{ auth.hasRole('ADMIN') ? 'Gestionar servicios' : 'Mis servicios' }}
            </a>
          }

          @if (auth.hasRole('ADMIN')) {
            <a routerLink="/admin/users" routerLinkActive="!bg-[#F1F0ED] !text-[#111111]" (click)="closeMenu()">Usuarios</a>
          }
        </nav>
      </div>

      <div class="pt-4">
        @if (!auth.isAuthenticated()) {
          <a routerLink="/login" (click)="closeMenu()"
             class="block w-full rounded-md bg-[#111111] px-4 py-3 text-center text-sm font-medium text-white transition-[background-color,transform] duration-150 hover:bg-[#333333] active:scale-[0.98] motion-reduce:active:scale-100">
            Ingresar
          </a>
        } @else {
          <div class="mb-4 rounded-md bg-[#F1F0ED] px-3 py-2.5">
            <p class="truncate text-sm font-medium text-[#111111]">{{ auth.email() }}</p>
            <p class="mt-0.5 text-xs uppercase tracking-wide text-[#787774]">{{ roleLabel() }}</p>
          </div>
          <button type="button" (click)="handleLogout()"
                  class="w-full cursor-pointer rounded-md border border-[#EAEAEA] px-4 py-3 text-center text-sm font-medium text-[#111111] transition-[background-color,transform] duration-150 hover:bg-[#F1F0ED] active:scale-[0.98] motion-reduce:active:scale-100">
            Cerrar sesión
          </button>
        }
      </div>
    </aside>

    <main class="bg-[#FBFBFA]">
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

  roleLabel(): string {
    switch (this.auth.role()) {
      case 'ADMIN': return 'Administrador';
      case 'PROVIDER': return 'Provider';
      case 'CUSTOMER': return 'Usuario';
      default: return '';
    }
  }

  handleLogout(): void {
    this.closeMenu();
    this.auth.logout();
  }
}
