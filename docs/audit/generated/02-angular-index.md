# Índice Angular / TypeScript

Este archivo contiene líneas indicadoras y ubicaciones, no volcados completos de código.

## Route configuration clues — 19 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/frontend/src/app/app.config.ts | 10 | provideRouter(routes), |
| audit-service/frontend/src/app/app.routes.ts | 1 | import { Routes } from '@angular/router'; |
| audit-service/frontend/src/app/app.routes.ts | 6 | export const routes: Routes = [ |
| audit-service/frontend/src/app/app.routes.ts | 7 | { path: 'login', component: LoginComponent }, |
| audit-service/frontend/src/app/app.routes.ts | 8 | { path: '', component: DashboardComponent, canActivate: [authGuard] }, |
| audit-service/frontend/src/app/app.routes.ts | 9 | { path: '**', redirectTo: '' } |
| frontend/src/main.ts | 13 | provideRouter(routes), |
| frontend/src/app/app.routes.ts | 2 | import { Routes } from '@angular/router'; |
| frontend/src/app/app.routes.ts | 14 | export const routes: Routes = [ |
| frontend/src/app/app.routes.ts | 15 | { path: '', component: HomeComponent }, |
| frontend/src/app/app.routes.ts | 18 | { path: 'login', component: LoginComponent, canActivate: [guestGuard] }, |
| frontend/src/app/app.routes.ts | 21 | { path: 'ai', component: AiComponent, canActivate: [authGuard] }, |
| frontend/src/app/app.routes.ts | 22 | { path: 'book', component: BookingComponent, canActivate: [authGuard] }, |
| frontend/src/app/app.routes.ts | 23 | { path: 'bookings/me', component: MyBookingsComponent, canActivate: [authGuard] }, |
| frontend/src/app/app.routes.ts | 26 | { path: 'provider/offerings', component: ProviderOfferingsComponent, canActivate: [roleGuard('PROVIDER', 'ADMIN')] }, |
| frontend/src/app/app.routes.ts | 27 | { path: 'admin/users', component: AdminUsersComponent, canActivate: [roleGuard('ADMIN')] }, |
| frontend/src/app/app.routes.ts | 29 | { path: '**', redirectTo: '' } |
| frontend/src/app/guards/role-guard.ts | 5 | // Uso: canActivate: [roleGuard('PROVIDER', 'ADMIN')] |
| frontend/src/app/guards/auth-guard.spec.ts | 15 | provideRouter([]), |

## HTTP/API call clues — 29 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/frontend/src/app/audit.service.ts | 42 | return this.http.get<BusinessEvent[]>(`${this.apiUrl}/events`); |
| audit-service/frontend/src/app/audit.service.ts | 46 | return this.http.get<BusinessEvent>(`${this.apiUrl}/events/${eventId}`); |
| audit-service/frontend/src/app/audit.service.ts | 50 | return this.http.get<AuditStats>(`${this.apiUrl}/stats`); |
| audit-service/frontend/src/app/login/login.component.ts | 29 | this.http.post<{token: string}>(authUrl, { |
| frontend/src/app/features/ai.component.ts | 333 | this.http.post<any>(url, payload).subscribe({ |
| frontend/src/app/features/ai.component.ts | 379 | this.http.post(`${apiBase()}/ai/tts`, { text: speechText }, { responseType: 'blob' }).subscribe({ |
| frontend/src/app/features/payment-modal/payment-modal.ts | 92 | const response = await this.http.post<any>(apiBase() + '/payments/authorize', payload, { |
| frontend/src/app/features/payment-modal/payment-modal.ts | 234 | this.http.post<any>(apiBase() + '/payments/authorize', payload, { |
| frontend/src/app/core/auth.service.ts | 20 | return this.http.post<AuthResponse>(`${apiBase()}/auth/login`, { email, password }) |
| frontend/src/app/core/auth.service.ts | 25 | return this.http.post<AuthResponse>(`${apiBase()}/auth/register`, { name, email, password }) |
| frontend/src/app/core/auth.service.ts | 34 | return this.http.get<AuthResponse>(`${apiBase()}/auth/me`).pipe( |
| frontend/src/app/core/auth.service.ts | 52 | this.http.post<void>(`${apiBase()}/auth/logout`, {}) |
| frontend/src/app/core/admin-user.service.ts | 31 | detail(id: string) { return this.http.get<AdminUser>(`${apiBase()}/admin/users/${id}`); } |
| frontend/src/app/core/admin-user.service.ts | 33 | return this.http.put<AdminUser>(`${apiBase()}/admin/users/${id}/status`, { status }); |
| frontend/src/app/core/admin-user.service.ts | 36 | return this.http.put<AdminUser>(`${apiBase()}/admin/users/${id}/role`, { role }); |
| frontend/src/app/core/booking.service.ts | 32 | return this.http.post<Booking>(apiBase() + '/bookings', payload, { headers }); |
| frontend/src/app/core/booking.service.ts | 36 | return this.http.get<Booking[]>(apiBase() + '/bookings/me'); |
| frontend/src/app/core/booking.service.ts | 41 | return this.http.patch<Booking>(apiBase() + '/bookings/' + bookingId + '/cancel', {}); |
| frontend/src/app/core/category.service.ts | 19 | return this.http.get<Category[]>(this.apiUrl); |
| frontend/src/app/core/category.service.ts | 23 | return this.http.post<Category>(this.apiUrl, payload); |
| frontend/src/app/core/category.service.ts | 27 | return this.http.put<Category>(`${this.apiUrl}/${id}`, payload); |
| frontend/src/app/core/category.service.ts | 31 | return this.http.delete<void>(`${this.apiUrl}/${id}`); |
| frontend/src/app/core/offering.service.ts | 27 | list() { return this.http.get<Offering[]>(`${apiBase()}/offerings`); } |
| frontend/src/app/core/offering.service.ts | 28 | listMine() { return this.http.get<Offering[]>(`${apiBase()}/offerings/me`); } |
| frontend/src/app/core/offering.service.ts | 29 | listAll() { return this.http.get<Offering[]>(`${apiBase()}/admin/offerings`); } |
| frontend/src/app/core/offering.service.ts | 30 | create(p: OfferingPayload) { return this.http.post<Offering>(`${apiBase()}/offerings`, p); } |
| frontend/src/app/core/offering.service.ts | 31 | update(id: string, p: OfferingPayload) { return this.http.put<Offering>(`${apiBase()}/offerings/${id}`, p); } |
| frontend/src/app/core/offering.service.ts | 32 | deactivate(id: string) { return this.http.post<void>(`${apiBase()}/offerings/${id}/deactivate`, {}); } |
| frontend/src/app/core/offering.service.ts | 33 | activate(id: string) { return this.http.post<void>(`${apiBase()}/offerings/${id}/activate`, {}); } |

## Guards / interceptors / state clues — 23 coincidencia(s)

| Archivo | Línea | Indicador |
|---|---|---|
| audit-service/frontend/src/app/app.config.ts | 3 | import { provideHttpClient, withInterceptors } from '@angular/common/http'; |
| audit-service/frontend/src/app/app.config.ts | 11 | provideHttpClient(withInterceptors([authInterceptor])) |
| audit-service/frontend/src/app/app.ts | 11 | protected readonly title = signal('frontend'); |
| audit-service/frontend/src/app/auth.interceptor.ts | 1 | import { HttpInterceptorFn } from '@angular/common/http'; |
| audit-service/frontend/src/app/auth.interceptor.ts | 3 | export const authInterceptor: HttpInterceptorFn = (req, next) => { |
| audit-service/frontend/src/app/auth.guard.ts | 2 | import { Router, CanActivateFn } from '@angular/router'; |
| audit-service/frontend/src/app/auth.guard.ts | 4 | export const authGuard: CanActivateFn = () => { |
| audit-service/frontend/src/app/login/login.component.ts | 18 | email = signal(''); |
| audit-service/frontend/src/app/login/login.component.ts | 19 | [línea omitida: posible valor sensible] |
| audit-service/frontend/src/app/login/login.component.ts | 20 | error = signal(''); |
| audit-service/frontend/src/app/login/login.component.ts | 21 | loading = signal(false); |
| frontend/src/main.ts | 4 | import { provideHttpClient, withInterceptors } from '@angular/common/http'; |
| frontend/src/main.ts | 14 | provideHttpClient(withInterceptors([authInterceptor])), |
| frontend/src/app/guards/role-guard.ts | 2 | import { CanActivateFn, Router } from '@angular/router'; |
| frontend/src/app/guards/role-guard.ts | 6 | export const roleGuard = (...roles: Role[]): CanActivateFn => () => { |
| frontend/src/app/guards/auth-guard.ts | 3 | import { CanActivateFn, Router } from '@angular/router'; |
| frontend/src/app/guards/auth-guard.ts | 6 | export const authGuard: CanActivateFn = (route, state) => { |
| frontend/src/app/guards/guest-guard.ts | 2 | import { CanActivateFn, Router } from '@angular/router'; |
| frontend/src/app/guards/guest-guard.ts | 5 | export const guestGuard: CanActivateFn = () => { |
| frontend/src/app/guards/auth-guard.spec.ts | 2 | import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router'; |
| frontend/src/app/guards/auth-guard.spec.ts | 7 | const run: CanActivateFn = (...args) => |
| frontend/src/app/core/auth.interceptor.ts | 1 | import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http'; |
| frontend/src/app/core/auth.interceptor.ts | 11 | export const authInterceptor: HttpInterceptorFn = (req, next) => { |

## Advertencia

La búsqueda es textual: puede omitir anotaciones multilínea o producir falsos positivos. Usa los resultados como índice de navegación.
