// Registro e inicio de sesión. Las cuentas y las contraseñas las gestiona
// UsersService; aquí solo vive el flujo de autenticación.

import {
  BadRequestException,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { UsersService } from '../users/users.service.js';
import { PasswordService } from '../users/password.service.js';
import { UserRole, UserStatus } from '../users/enums/index.js';
import { UserResponseDto } from '../users/dto/index.js';
import {
  ForgotPasswordDto,
  LoginDto,
  LoginResponseDto,
  MessageResponseDto,
  RegisterDto,
  ResetPasswordDto,
} from './dto/index.js';
import { PasswordResetToken } from './entities/password-reset-token.entity.js';
import { MailService } from './mail/index.js';
import {
  generateResetToken,
  hashResetToken,
} from './password-reset-token.util.js';
import type { JwtPayload } from './interfaces/index.js';

// Mismo mensaje para email inexistente, contraseña incorrecta o cuenta
// inactiva: no revela cuál de las tres falló (RN-091)
const INVALID_CREDENTIALS = 'Credenciales inválidas';

// Misma respuesta exista o no la cuenta: no revela qué correos están registrados
const FORGOT_PASSWORD_MESSAGE =
  'Si el correo está registrado, recibirás un enlace para restablecer tu contraseña';

// Mismo error para token inexistente, expirado o ya usado (RN-103, RN-104)
const INVALID_RESET_TOKEN =
  'El enlace de recuperación es inválido o ha expirado';

@Injectable()
export class AuthService {
  // Hash de una contraseña que nadie conoce. Se compara contra él cuando el
  // email no existe, para que esa respuesta tarde lo mismo que una contraseña
  // incorrecta y no sirva para averiguar qué correos están registrados.
  private readonly dummyHash: Promise<string>;
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly usersService: UsersService,
    private readonly passwordService: PasswordService,
    private readonly jwtService: JwtService,
    private readonly mailService: MailService,
    private readonly config: ConfigService,
    @InjectRepository(PasswordResetToken)
    private readonly resetTokenRepository: Repository<PasswordResetToken>,
  ) {
    this.dummyHash = this.passwordService.hash(crypto.randomUUID());
  }

  // Registro público: siempre crea un CUSTOMER ACTIVE (RN-086). El rol no se
  // acepta desde el body; los empleados se dan de alta en HU-016.
  async register(dto: RegisterDto): Promise<UserResponseDto> {
    const user = await this.usersService.create({
      firstName: dto.firstName,
      lastName: dto.lastName,
      email: dto.email,
      phone: dto.phone,
      password: dto.password,
      role: UserRole.CUSTOMER,
    });
    return UserResponseDto.from(user);
  }

  // RN-088 … RN-093
  async login(dto: LoginDto): Promise<LoginResponseDto> {
    const user = await this.usersService.findByEmailWithPassword(dto.email);
    const passwordMatches = await this.passwordService.compare(
      dto.password,
      user?.passwordHash ?? (await this.dummyHash),
    );

    if (!user || !passwordMatches || user.status !== UserStatus.ACTIVE) {
      throw new UnauthorizedException(INVALID_CREDENTIALS);
    }

    const payload: JwtPayload = {
      sub: user.id,
      email: user.email,
      role: user.role,
    };

    return {
      accessToken: await this.jwtService.signAsync(payload),
      user: {
        id: user.id,
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        role: user.role,
      },
    };
  }

  // HU-018: inicia la recuperación. Responde siempre lo mismo, exista o no la
  // cuenta y falle o no el envío, para no filtrar qué correos están registrados.
  async forgotPassword(dto: ForgotPasswordDto): Promise<MessageResponseDto> {
    const user = await this.usersService.findByEmail(dto.email);

    // Una cuenta INACTIVE no puede iniciar sesión (RN-090), así que tampoco recupera
    if (user && user.status === UserStatus.ACTIVE) {
      const token = generateResetToken();
      const ttlMinutes = this.config.getOrThrow<number>(
        'passwordReset.ttlMinutes',
      );

      // Un solo token vigente por usuario: pedir otro anula los anteriores
      await this.resetTokenRepository.manager.transaction(async (manager) => {
        await manager.delete(PasswordResetToken, {
          userId: user.id,
          usedAt: IsNull(),
        });
        await manager.save(
          manager.create(PasswordResetToken, {
            userId: user.id,
            tokenHash: hashResetToken(token),
            expiresAt: new Date(Date.now() + ttlMinutes * 60_000),
            usedAt: null,
          }),
        );
      });

      try {
        await this.mailService.sendPasswordReset(
          user.email,
          this.buildResetLink(token),
        );
      } catch (error) {
        // Un fallo de envío no debe distinguirse desde fuera de "no existe"
        this.logger.error(
          `No se pudo enviar la recuperación de contraseña: ${
            error instanceof Error ? error.message : String(error)
          }`,
        );
      }
    }

    return { message: FORGOT_PASSWORD_MESSAGE };
  }

  // HU-018: define la nueva contraseña con un token vigente y sin usar (RN-103 … RN-106).
  async resetPassword(dto: ResetPasswordDto): Promise<MessageResponseDto> {
    await this.resetTokenRepository.manager.transaction(async (manager) => {
      // Consumir el token y comprobarlo es una sola sentencia atómica: dos
      // peticiones simultáneas con el mismo token no pueden usarlo las dos
      const result = await manager
        .createQueryBuilder()
        .update(PasswordResetToken)
        .set({ usedAt: () => 'now()' })
        .where('"tokenHash" = :hash', { hash: hashResetToken(dto.token) })
        .andWhere('"usedAt" IS NULL')
        .andWhere('"expiresAt" > now()')
        .returning(['userId'])
        .execute();

      const consumed = (result.raw as { userId: string }[])[0];
      if (!consumed) throw new BadRequestException(INVALID_RESET_TOKEN);

      const user = await this.usersService.findById(consumed.userId);
      if (!user || user.status !== UserStatus.ACTIVE) {
        // Si lanza, la transacción deshace el "usado" del token
        throw new BadRequestException(INVALID_RESET_TOKEN);
      }

      await this.usersService.setPassword(user.id, dto.password, manager);
      // Cualquier otro token pendiente del usuario deja de servir
      await manager.delete(PasswordResetToken, {
        userId: user.id,
        usedAt: IsNull(),
      });
    });

    return { message: 'Contraseña actualizada. Ya puedes iniciar sesión' };
  }

  private buildResetLink(token: string): string {
    const url = new URL(this.config.getOrThrow<string>('passwordReset.url'));
    url.searchParams.set('token', token);
    return url.toString();
  }
}
