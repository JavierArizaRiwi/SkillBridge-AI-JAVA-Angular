import type { UserRole } from '../../users/enums/index.js';

// Contenido del access token (RN-092)
export interface JwtPayload {
  sub: string;
  email: string;
  role: UserRole;
}

// Lo que @CurrentUser() entrega a los controladores
export interface AuthenticatedUser {
  id: string;
  email: string;
  role: UserRole;
}
