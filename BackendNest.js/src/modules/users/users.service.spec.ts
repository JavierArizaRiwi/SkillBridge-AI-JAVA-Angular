import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { QueryFailedError, Repository } from 'typeorm';
import { BusinessRuleException } from '../../common/exceptions/index.js';
import { User } from './entities/user.entity.js';
import { UserRole, UserStatus } from './enums/index.js';
import { PasswordService } from './password.service.js';
import { UsersService } from './users.service.js';

type MockRepository = Partial<Record<keyof Repository<User>, Mock>>;

const data = {
  firstName: ' Carlos ',
  lastName: 'Pérez',
  email: ' Carlos@Example.com ',
  phone: '+57 300 123 4567',
  password: 'Password123',
};

describe('UsersService', () => {
  let service: UsersService;
  let repository: MockRepository;
  let passwordService: { hash: Mock; compare: Mock };

  beforeEach(async () => {
    repository = {
      existsBy: vi.fn().mockResolvedValue(false),
      create: vi.fn((entity: Partial<User>) => entity),
      save: vi.fn((entity: Partial<User>) =>
        Promise.resolve({ ...entity, id: 'u-1' }),
      ),
      findOneByOrFail: vi.fn(({ id }: { id: string }) =>
        Promise.resolve({ id, email: 'carlos@example.com' }),
      ),
    };
    passwordService = {
      hash: vi.fn().mockResolvedValue('scrypt$hash'),
      compare: vi.fn(),
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        UsersService,
        { provide: getRepositoryToken(User), useValue: repository },
        { provide: PasswordService, useValue: passwordService },
      ],
    }).compile();

    service = moduleRef.get(UsersService);
  });

  describe('create', () => {
    it('guarda el hash, el email en minúsculas, rol CUSTOMER y estado ACTIVE (RN-085, RN-086)', async () => {
      await service.create(data);

      expect(passwordService.hash).toHaveBeenCalledWith('Password123');
      expect(repository.create).toHaveBeenCalledWith({
        firstName: 'Carlos',
        lastName: 'Pérez',
        email: 'carlos@example.com',
        phone: '+57 300 123 4567',
        passwordHash: 'scrypt$hash',
        role: UserRole.CUSTOMER,
        status: UserStatus.ACTIVE,
      });
    });

    it('respeta el rol indicado (alta de empleados, HU-016)', async () => {
      await service.create({ ...data, role: UserRole.WAITER });

      expect(repository.create).toHaveBeenCalledWith(
        expect.objectContaining({ role: UserRole.WAITER }),
      );
    });

    it('devuelve el usuario releído de la base, sin el hash', async () => {
      const user = await service.create(data);

      expect(repository.findOneByOrFail).toHaveBeenCalledWith({ id: 'u-1' });
      expect(user).not.toHaveProperty('passwordHash');
    });

    it('rechaza un email ya registrado con 409 RN-082, sin calcular el hash', async () => {
      repository.existsBy!.mockResolvedValue(true);

      await expect(service.create(data)).rejects.toMatchObject({
        constructor: BusinessRuleException,
        response: expect.objectContaining({ rule: 'RN-082' }),
      });
      expect(repository.existsBy).toHaveBeenCalledWith({
        email: 'carlos@example.com',
      });
      expect(passwordService.hash).not.toHaveBeenCalled();
    });

    it('convierte la violación del índice único (registro simultáneo) en 409 RN-082', async () => {
      const driverError = Object.assign(new Error('duplicate key'), {
        code: '23505',
      });
      repository.save!.mockRejectedValue(
        new QueryFailedError('INSERT', [], driverError),
      );

      await expect(service.create(data)).rejects.toBeInstanceOf(
        BusinessRuleException,
      );
    });

    it('no oculta otros errores de base de datos', async () => {
      repository.save!.mockRejectedValue(new Error('conexión perdida'));

      await expect(service.create(data)).rejects.toThrow('conexión perdida');
    });
  });
});
