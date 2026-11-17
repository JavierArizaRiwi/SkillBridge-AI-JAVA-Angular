export type UserRole = 'CUSTOMER' | 'PROVIDER' | 'ADMIN';

/** Usuario autenticado derivado de los claims RS256 (security.md). */
export interface AuthenticatedUser {
  uid: string;
  email: string;
  role: UserRole;
}

export interface JwtClaims {
  uid?: string;
  email?: string;
  sub?: string;
  role?: UserRole;
  iss?: string;
}
