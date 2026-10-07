import { Routes } from '@angular/router';
import { HomeComponent } from './features/home.component';
import { LoginComponent } from './features/login.component';
import { AiComponent } from './features/ai.component';
import { BookingComponent } from './features/booking.component';
import { authGuard } from './guards/auth-guard';
import { MyBookingsComponent } from './features/my-bookings.component';
import { ProviderOfferingsComponent } from './features/provider-offerings.component';
import { roleGuard } from './guards/role-guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },

  // Vistas protegidas:
  { path: 'ai', component: AiComponent, canActivate: [authGuard] },
  { path: 'book', component: BookingComponent, canActivate: [authGuard] },
  { path: 'bookings/me', component: MyBookingsComponent, canActivate: [authGuard]},

  // Solo Provider y Admin:
  { path: 'provider/offerings', component: ProviderOfferingsComponent, canActivate: [roleGuard('PROVIDER', 'ADMIN')] },

  { path: '**', redirectTo: '' }
];
