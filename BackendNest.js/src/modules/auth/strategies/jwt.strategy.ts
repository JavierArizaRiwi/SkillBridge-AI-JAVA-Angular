import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { UsersService } from '../../users/users.service.js';
import { UserStatus } from '../../users/enums/index.js';
import type { AuthenticatedUser, JwtPayload } from '../interfaces/index.js';

// Valida el "Authorization: Bearer <token>" de cada petición protegida.
// passport-jwt ya comprueba la firma y la expiración; aquí además se
// rechaza el token si la cuenta se borró o se desactivó después de emitirlo.
@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    config: ConfigService,
    private readonly usersService: UsersService,
  ) {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: config.getOrThrow<string>('jwt.secret'),
    });
  }

  async validate(payload: JwtPayload): Promise<AuthenticatedUser> {
    const user = await this.usersService.findById(payload.sub);
    if (!user || user.status !== UserStatus.ACTIVE) {
      throw new UnauthorizedException('Token inválido o expirado');
    }
    // El rol se lee de la base, no del token: un cambio de rol aplica al momento
    return { id: user.id, email: user.email, role: user.role };
  }
}
