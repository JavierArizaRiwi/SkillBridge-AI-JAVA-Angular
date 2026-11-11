// app.routes.ts
import { Routes } from '@angular/router';
import { authGuard } from './guards/auth-guard';   // ajusta las rutas de import
import { guestGuard } from './guards/guest-guard';
import { roleGuard } from './guards/role-guard';
import { HomeComponent } from './features/home.component';
import { LoginComponent } from './features/login.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { MyBookingsComponent } from './features/my-bookings.component';
import { ProviderOfferingsComponent } from './features/provider-offerings.component';
import { AdminUsersComponent } from './features/admin-users.component';
import { TrendingComponent } from './features/trending.component';
import { MyMentorshipsComponent } from './features/my-mentorships.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },

  // Solo para quien NO ha iniciado sesión:
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },

  // Vistas protegidas:
  { path: 'ai', component: AiComponent, canActivate: [authGuard] },
  { path: 'book', component: BookingComponent, canActivate: [authGuard] },
  { path: 'bookings/me', component: MyBookingsComponent, canActivate: [authGuard] },
  { path: 'trending', component: TrendingComponent, canActivate: [authGuard] },
  { path: 'mentorships/me', component: MyMentorshipsComponent, canActivate: [authGuard] },

  // Solo Provider y Admin:
  { path: 'provider/offerings', component: ProviderOfferingsComponent, canActivate: [roleGuard('PROVIDER', 'ADMIN')] },
  { path: 'admin/users', component: AdminUsersComponent, canActivate: [roleGuard('ADMIN')] },

  { path: '**', redirectTo: '' }
];
