// Prueba EmployeesService SIN base de datos: el repositorio, la transacción
// y UsersService son mocks que responden lo que cada test les indique.

import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { User } from '../users/entities/user.entity.js';
import { UserRole, UserStatus } from '../users/enums/index.js';
import { UsersService } from '../users/users.service.js';
import { Employee } from './entities/employee.entity.js';
import { EmployeePosition } from './enums/index.js';
import { EmployeesService } from './employees.service.js';

const user = (): User => ({
  id: 'user-1',
  firstName: 'Ana',
  lastName: 'López',
  email: 'ana@example.com',
  phone: '+57 300 123 4567',
  passwordHash: undefined as unknown as string,
  role: UserRole.WAITER,
  status: UserStatus.ACTIVE,
  createdAt: new Date(),
  updatedAt: new Date(),
});

const employee = (): Employee => ({
  id: 'employee-1',
  userId: 'user-1',
  user: user(),
  position: EmployeePosition.WAITER,
  createdAt: new Date(),
  updatedAt: new Date(),
});

const createDto = {
  firstName: 'Ana',
  lastName: 'López',
  email: 'ana@example.com',
  phone: '+57 300 123 4567',
  position: EmployeePosition.COOK,
  password: 'Password123',
};

describe('EmployeesService', () => {
  let service: EmployeesService;
  let repository: { find: Mock; findOne: Mock; manager: Record<string, Mock> };
  let manager: { create: Mock; save: Mock };
  let usersService: { findByEmail: Mock; create: Mock };

  beforeEach(async () => {
    // El manager de la transacción: create arma la entidad y save le pone id
    manager = {
      create: vi.fn((_entity: unknown, data: Partial<Employee>) => data),
      save: vi.fn((...args: unknown[]) =>
        Promise.resolve({ id: 'employee-1', ...(args.at(-1) as object) }),
      ),
    };
    repository = {
      find: vi.fn(),
      findOne: vi.fn(),
      manager: {
        transaction: vi.fn((work: (m: typeof manager) => unknown) =>
          work(manager),
        ),
        save: vi.fn(),
      },
    };
    usersService = {
      findByEmail: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockResolvedValue({ ...user(), role: UserRole.KITCHEN }),
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        EmployeesService,
        { provide: getRepositoryToken(Employee), useValue: repository },
        { provide: UsersService, useValue: usersService },
      ],
    }).compile();

    service = moduleRef.get(EmployeesService);
  });

  describe('create', () => {
    it('crea la cuenta con el rol del cargo y el perfil en la misma transacción', async () => {
      const result = await service.create(createDto);

      expect(repository.manager.transaction).toHaveBeenCalledOnce();
      expect(usersService.create).toHaveBeenCalledWith(
        expect.objectContaining({
          email: 'ana@example.com',
          password: 'Password123',
          role: UserRole.KITCHEN,
        }),
        manager,
      );
      expect(manager.create).toHaveBeenCalledWith(Employee, {
        userId: 'user-1',
        position: EmployeePosition.COOK,
      });
      expect(result).toMatchObject({
        id: 'employee-1',
        userId: 'user-1',
        email: 'ana@example.com',
        position: EmployeePosition.COOK,
        role: UserRole.KITCHEN,
        status: UserStatus.ACTIVE,
      });
      expect(result).not.toHaveProperty('passwordHash');
    });

    it('con un correo ya registrado lanza 409 RN-095 y no crea nada', async () => {
      usersService.findByEmail.mockResolvedValue(user());

      const error = await service.create(createDto).catch((e: unknown) => e);

      expect(error).toBeInstanceOf(BusinessRuleException);
      expect((error as BusinessRuleException).getResponse()).toMatchObject({
        rule: 'RN-095',
      });
      expect(repository.manager.transaction).not.toHaveBeenCalled();
    });

    it('si otra alta con el mismo correo gana la carrera, también responde RN-095', async () => {
      usersService.create.mockRejectedValue(
        new BusinessRuleException(
          'Ya existe una cuenta con ese correo electrónico',
          'RN-082',
        ),
      );

      const error = await service.create(createDto).catch((e: unknown) => e);

      expect(error).toBeInstanceOf(BusinessRuleException);
      expect((error as BusinessRuleException).getResponse()).toMatchObject({
        rule: 'RN-095',
      });
    });
  });

  it('findAll devuelve los empleados con los datos de su cuenta', async () => {
    repository.find.mockResolvedValue([employee()]);

    const result = await service.findAll();

    expect(repository.find).toHaveBeenCalledWith(
      expect.objectContaining({ relations: { user: true } }),
    );
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({ id: 'employee-1', firstName: 'Ana' });
  });

  describe('findOne', () => {
    it('devuelve el empleado', async () => {
      repository.findOne.mockResolvedValue(employee());

      await expect(service.findOne('employee-1')).resolves.toMatchObject({
        id: 'employee-1',
        email: 'ana@example.com',
      });
    });

    it('inexistente lanza EntityNotFoundException (404)', async () => {
      repository.findOne.mockResolvedValue(null);

      await expect(service.findOne('nope')).rejects.toBeInstanceOf(
        EntityNotFoundException,
      );
    });
  });

  describe('update', () => {
    it('cambia solo los campos enviados, en una transacción', async () => {
      repository.findOne.mockResolvedValue(employee());

      const result = await service.update('employee-1', {
        phone: '+57 311 000 0000',
        position: EmployeePosition.CASHIER,
      });

      expect(repository.manager.transaction).toHaveBeenCalledOnce();
      expect(manager.save).toHaveBeenCalledWith(
        User,
        expect.objectContaining({
          firstName: 'Ana',
          phone: '+57 311 000 0000',
        }),
      );
      expect(result).toMatchObject({
        firstName: 'Ana',
        phone: '+57 311 000 0000',
        position: EmployeePosition.CASHIER,
      });
    });

    it('inexistente lanza 404 y no guarda nada', async () => {
      repository.findOne.mockResolvedValue(null);

      await expect(
        service.update('nope', { firstName: 'X' }),
      ).rejects.toBeInstanceOf(EntityNotFoundException);
      expect(repository.manager.transaction).not.toHaveBeenCalled();
    });
  });

  describe('updateStatus', () => {
    it('desactiva la cuenta del empleado (RN-098)', async () => {
      repository.findOne.mockResolvedValue(employee());

      const result = await service.updateStatus('employee-1', {
        status: UserStatus.INACTIVE,
      });

      expect(repository.manager.save).toHaveBeenCalledWith(
        User,
        expect.objectContaining({ id: 'user-1', status: UserStatus.INACTIVE }),
      );
      expect(result.status).toBe(UserStatus.INACTIVE);
    });

    it('inexistente lanza 404', async () => {
      repository.findOne.mockResolvedValue(null);

      await expect(
        service.updateStatus('nope', { status: UserStatus.ACTIVE }),
      ).rejects.toBeInstanceOf(EntityNotFoundException);
    });
  });
});
