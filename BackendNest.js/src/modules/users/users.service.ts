// Cuentas de usuario. Lo usan el registro y el login (auth), y lo usarán
// empleados (HU-016) y perfil (HU-019). Recibe la contraseña en claro y
// guarda solo su hash: nadie más debería tocar passwordHash.

import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { EntityManager, QueryFailedError, Repository } from 'typeorm';
import { BusinessRuleException } from '../../common/exceptions/index.js';
import { User } from './entities/user.entity.js';
import { UserRole, UserStatus } from './enums/index.js';
import { PasswordService } from './password.service.js';

export interface CreateUserData {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
  role?: UserRole;
}

// Código de PostgreSQL para "violación de restricción única"
const UNIQUE_VIOLATION = '23505';

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
    private readonly passwordService: PasswordService,
  ) {}

  // Crea una cuenta ACTIVE (RN-086). Email repetido → 409 (RN-082). Acepta el
  // manager de una transacción para crearla junto con otros datos (HU-016).
  async create(data: CreateUserData, manager?: EntityManager): Promise<User> {
    const repository = manager?.getRepository(User) ?? this.userRepository;
    const email = this.normalizeEmail(data.email);

    if (await repository.existsBy({ email })) {
      throw this.duplicatedEmail();
    }

    const user = repository.create({
      firstName: data.firstName.trim(),
      lastName: data.lastName.trim(),
      email,
      phone: data.phone.trim(),
      passwordHash: await this.passwordService.hash(data.password),
      role: data.role ?? UserRole.CUSTOMER,
      status: UserStatus.ACTIVE,
    });

    try {
      const saved = await repository.save(user);
      // Lo devolvemos recién leído, sin passwordHash (select: false)
      return await repository.findOneByOrFail({ id: saved.id });
    } catch (error) {
      // Dos registros simultáneos con el mismo email: el índice único decide
      if (
        error instanceof QueryFailedError &&
        (error.driverError as { code?: string }).code === UNIQUE_VIOLATION
      ) {
        throw this.duplicatedEmail();
      }
      throw error;
    }
  }

  findByEmail(email: string): Promise<User | null> {
    return this.userRepository.findOneBy({ email: this.normalizeEmail(email) });
  }

  // Cambia la contraseña guardando solo su hash (RN-106). Acepta el manager de
  // una transacción para que el cambio y lo que lo acompañe sean atómicos.
  async setPassword(
    userId: string,
    password: string,
    manager?: EntityManager,
  ): Promise<void> {
    const repository = manager?.getRepository(User) ?? this.userRepository;
    await repository.update(
      { id: userId },
      { passwordHash: await this.passwordService.hash(password) },
    );
  }

  findById(id: string): Promise<User | null> {
    return this.userRepository.findOneBy({ id });
  }

  // Para el login: incluye passwordHash, que por defecto no se selecciona.
  findByEmailWithPassword(email: string): Promise<User | null> {
    return this.userRepository
      .createQueryBuilder('user')
      .addSelect('user.passwordHash')
      .where('user.email = :email', { email: this.normalizeEmail(email) })
      .getOne();
  }

  private normalizeEmail(email: string): string {
    return email.trim().toLowerCase();
  }

  private duplicatedEmail(): BusinessRuleException {
    return new BusinessRuleException(
      'Ya existe una cuenta con ese correo electrónico',
      'RN-082',
    );
  }
}
