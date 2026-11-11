import {
  Injectable,
  UnauthorizedException,
  type ExecutionContext,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthGuard } from '@nestjs/passport';
import { IS_PUBLIC_KEY } from '../decorators/index.js';

// Guard global (APP_GUARD en AuthModule): toda ruta exige JWT salvo las
// marcadas con @Public() (RN-094).
@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {
  constructor(private readonly reflector: Reflector) {
    super();
  }

  canActivate(context: ExecutionContext) {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    return isPublic ? true : super.canActivate(context);
  }

  // Un solo mensaje para token ausente, mal formado, con firma inválida o
  // expirado: no se da pista de cuál fue el problema
  handleRequest<TUser>(error: unknown, user: TUser | false): TUser {
    if (error || !user) {
      throw new UnauthorizedException('Token inválido o expirado');
    }
    return user;
  }
}
