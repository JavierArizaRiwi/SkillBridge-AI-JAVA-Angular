// Prueba ProfileService SIN base de datos: el repositorio es un mock.

import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { EntityNotFoundException } from '../../common/exceptions/index.js';
import { User } from '../users/entities/user.entity.js';
import { UserRole, UserStatus } from '../users/enums/index.js';
import { ProfileService } from './profile.service.js';

const user = (): User => ({
  id: 'user-1',
  firstName: 'Carlos',
  lastName: 'Pérez',
  email: 'carlos@example.com',
  phone: '+57 300 123 4567',
  passwordHash: 'scrypt$hash',
  role: UserRole.CUSTOMER,
  status: UserStatus.ACTIVE,
  createdAt: new Date(),
  updatedAt: new Date(),
});

describe('ProfileService', () => {
  let service: ProfileService;
  let repository: { findOneBy: Mock; save: Mock };

  beforeEach(async () => {
    repository = {
      findOneBy: vi.fn().mockResolvedValue(user()),
      save: vi.fn((entity: User) => Promise.resolve(entity)),
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        ProfileService,
        { provide: getRepositoryToken(User), useValue: repository },
      ],
    }).compile();

    service = moduleRef.get(ProfileService);
  });

  it('devuelve el perfil del usuario del token, sin la contraseña (RN-107, RN-108)', async () => {
    const result = await service.findMe('user-1');

    expect(repository.findOneBy).toHaveBeenCalledWith({ id: 'user-1' });
    expect(result).toMatchObject({ id: 'user-1', email: 'carlos@example.com' });
    expect(result).not.toHaveProperty('passwordHash');
  });

  it('actualiza solo los campos enviados', async () => {
    const result = await service.update('user-1', {
      phone: '+57 311 000 0000',
    });

    expect(repository.save).toHaveBeenCalledWith(
      expect.objectContaining({
        firstName: 'Carlos',
        phone: '+57 311 000 0000',
      }),
    );
    expect(result).toMatchObject({
      firstName: 'Carlos',
      phone: '+57 311 000 0000',
    });
    expect(result).not.toHaveProperty('passwordHash');
  });

  it('si la cuenta ya no existe lanza 404', async () => {
    repository.findOneBy.mockResolvedValue(null);

    await expect(service.findMe('nope')).rejects.toBeInstanceOf(
      EntityNotFoundException,
    );
  });
});
