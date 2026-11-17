import { type CanActivate, type ExecutionContext, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import jwt from 'jsonwebtoken';
import type { Request } from 'express';
import { DomainErrors } from '../common/domain-error.js';
import type { AuthenticatedUser, JwtClaims, UserRole } from './auth.types.js';

const VALID_ROLES: readonly UserRole[] = ['CUSTOMER', 'PROVIDER', 'ADMIN'];

interface AuthRequest extends Request {
  user?: AuthenticatedUser;
}

/**
 * Valida JWT RS256 emitidos por el monolito (security.md): firma, `iss`,
 * expiración y algoritmo. Acepta `Authorization: Bearer` o cookie
 * `access_token`; con cookie exige `X-XSRF-TOKEN` (rest-api.md).
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<AuthRequest>();
    const { token, fromCookie } = this.extractToken(request);

    if (!token) {
      throw DomainErrors.unauthenticated('Falta el token de acceso');
    }

    if (fromCookie) {
      const xsrf = request.headers['x-xsrf-token'];
      const headerValue = Array.isArray(xsrf) ? xsrf[0] : xsrf;
      if (!headerValue || headerValue.trim().length === 0) {
        throw DomainErrors.forbidden('Falta X-XSRF-TOKEN', 'CSRF');
      }
    }

    const publicKey = this.config.get<string>('jwt.publicKey');
    if (!publicKey) {
      throw DomainErrors.unauthenticated('JWT_PUBLIC_KEY no configurada');
    }

    let claims: JwtClaims;
    try {
      claims = jwt.verify(token, publicKey, {
        algorithms: ['RS256'],
        issuer: this.config.get<string>('jwt.issuer') ?? 'skillbridge',
      }) as JwtClaims;
    } catch {
      throw DomainErrors.unauthenticated('Token inválido o expirado');
    }

    const role = claims.role;
    if (!claims.uid || !role || !VALID_ROLES.includes(role)) {
      throw DomainErrors.unauthenticated('Claims de token incompletos');
    }

    request.user = {
      uid: claims.uid,
      email: claims.email ?? claims.sub ?? '',
      role,
    };
    return true;
  }

  private extractToken(request: AuthRequest): {
    token: string | null;
    fromCookie: boolean;
  } {
    const header = request.headers.authorization;
    if (header?.startsWith('Bearer ')) {
      return { token: header.slice('Bearer '.length).trim(), fromCookie: false };
    }

    const cookies = this.parseCookies(request.headers.cookie);
    if (cookies.access_token) {
      return { token: cookies.access_token, fromCookie: true };
    }

    return { token: null, fromCookie: false };
  }

  private parseCookies(header?: string): Record<string, string> {
    if (!header) {
      return {};
    }
    return header.split(';').reduce<Record<string, string>>((acc, part) => {
      const index = part.indexOf('=');
      if (index > -1) {
        const key = part.slice(0, index).trim();
        acc[key] = decodeURIComponent(part.slice(index + 1).trim());
      }
      return acc;
    }, {});
  }
}
