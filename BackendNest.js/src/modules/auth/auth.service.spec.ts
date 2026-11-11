import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { getRepositoryToken } from '@nestjs/typeorm';
import { UsersService } from '../users/users.service.js';
import { PasswordService } from '../users/password.service.js';
import { User } from '../users/entities/user.entity.js';
import { UserRole, UserStatus } from '../users/enums/index.js';
import { AuthService } from './auth.service.js';
import { RegisterDto } from './dto/index.js';
import { PasswordResetToken } from './entities/password-reset-token.entity.js';
import { MailService } from './mail/index.js';
import { hashResetToken } from './password-reset-token.util.js';

const user: User = {
  id: 'u-1',
  firstName: 'Carlos',
  lastName: 'Pérez',
  email: 'carlos@example.com',
  phone: '+57 300 123 4567',
  passwordHash: 'scrypt$hash',
  role: UserRole.CUSTOMER,
  status: UserStatus.ACTIVE,
  createdAt: new Date('2026-10-07T12:00:00Z'),
  updatedAt: new Date('2026-10-07T12:00:00Z'),
};

const dto: RegisterDto = {
  firstName: 'Carlos',
  lastName: 'Pérez',
  email: 'carlos@example.com',
  phone: '+57 300 123 4567',
  password: 'Password123',
  passwordConfirmation: 'Password123',
};

describe('AuthService', () => {
  let service: AuthService;
  let usersService: { create: Mock; findByEmail: Mock };
  let mailService: { sendPasswordReset: Mock };
  // Manager falso: la transacción ejecuta directamente su callback
  let manager: { delete: Mock; create: Mock; save: Mock };

  beforeEach(async () => {
    usersService = {
      create: vi.fn().mockResolvedValue(user),
      findByEmail: vi.fn().mockResolvedValue(user),
    };
    mailService = { sendPasswordReset: vi.fn().mockResolvedValue(undefined) };
    manager = {
      delete: vi.fn().mockResolvedValue(undefined),
      create: vi.fn((_entity: unknown, data: object) => data),
      save: vi.fn().mockResolvedValue(undefined),
    };
    const moduleRef = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: UsersService, useValue: usersService },
        {
          provide: PasswordService,
          useValue: {
            hash: vi.fn().mockResolvedValue('scrypt$dummy'),
            compare: vi.fn(),
          },
        },
        { provide: JwtService, useValue: { signAsync: vi.fn() } },
        { provide: MailService, useValue: mailService },
        {
          provide: ConfigService,
          useValue: {
            getOrThrow: (key: string) =>
              key === 'passwordReset.ttlMinutes'
                ? 30
                : 'http://localhost:3000/reset-password',
          },
        },
        {
          provide: getRepositoryToken(PasswordResetToken),
          useValue: {
            manager: {
              transaction: (work: (m: typeof manager) => Promise<void>) =>
                work(manager),
            },
          },
        },
      ],
    }).compile();
    service = moduleRef.get(AuthService);
  });

  describe('register', () => {
    it('crea siempre un CUSTOMER y no pasa la confirmación a UsersService', async () => {
      await service.register(dto);

      expect(usersService.create).toHaveBeenCalledWith({
        firstName: 'Carlos',
        lastName: 'Pérez',
        email: 'carlos@example.com',
        phone: '+57 300 123 4567',
        password: 'Password123',
        role: UserRole.CUSTOMER,
      });
    });

    it('responde sin contraseña ni hash (RN-087)', async () => {
      const res = await service.register(dto);

      expect(res).toEqual({
        id: 'u-1',
        firstName: 'Carlos',
        lastName: 'Pérez',
        email: 'carlos@example.com',
        phone: '+57 300 123 4567',
        role: UserRole.CUSTOMER,
        status: UserStatus.ACTIVE,
        createdAt: user.createdAt,
      });
    });
  });

  describe('forgotPassword (RN-103)', () => {
    const request = { email: 'carlos@example.com' };
    const GENERIC =
      'Si el correo está registrado, recibirás un enlace para restablecer tu contraseña';

    it('guarda solo el hash del token y envía el enlace con el token en claro', async () => {
      await service.forgotPassword(request);

      const [to, link] = mailService.sendPasswordReset.mock.calls[0] as [
        string,
        string,
      ];
      const token = new URL(link).searchParams.get('token')!;

      expect(to).toBe('carlos@example.com');
      expect(link.startsWith('http://localhost:3000/reset-password?')).toBe(
        true,
      );
      expect(token).toHaveLength(43);

      const saved = manager.save.mock.calls[0][0] as {
        tokenHash: string;
        userId: string;
        expiresAt: Date;
      };
      expect(saved.tokenHash).toBe(hashResetToken(token));
      expect(saved.tokenHash).not.toContain(token);
      expect(saved.userId).toBe('u-1');
    });

    it('el token vence a los minutos configurados (30)', async () => {
      const before = Date.now();
      await service.forgotPassword(request);

      const { expiresAt } = manager.save.mock.calls[0][0] as {
        expiresAt: Date;
      };
      const minutes = (expiresAt.getTime() - before) / 60_000;
      expect(minutes).toBeGreaterThan(29.9);
      expect(minutes).toBeLessThan(30.1);
    });

    it('anula los tokens pendientes anteriores del usuario', async () => {
      await service.forgotPassword(request);

      expect(manager.delete).toHaveBeenCalledWith(
        PasswordResetToken,
        expect.objectContaining({ userId: 'u-1' }),
      );
    });

    it('responde lo mismo y no envía nada si el correo no existe', async () => {
      usersService.findByEmail.mockResolvedValue(null);

      await expect(service.forgotPassword(request)).resolves.toEqual({
        message: GENERIC,
      });
      expect(manager.save).not.toHaveBeenCalled();
      expect(mailService.sendPasswordReset).not.toHaveBeenCalled();
    });

    it('no envía nada a una cuenta INACTIVE, pero responde igual', async () => {
      usersService.findByEmail.mockResolvedValue({
        ...user,
        status: UserStatus.INACTIVE,
      });

      await expect(service.forgotPassword(request)).resolves.toEqual({
        message: GENERIC,
      });
      expect(mailService.sendPasswordReset).not.toHaveBeenCalled();
    });

    it('si el envío falla responde igual (un error no debe delatar que la cuenta existe)', async () => {
      mailService.sendPasswordReset.mockRejectedValue(new Error('SMTP caído'));

      await expect(service.forgotPassword(request)).resolves.toEqual({
        message: GENERIC,
      });
    });
  });
});
