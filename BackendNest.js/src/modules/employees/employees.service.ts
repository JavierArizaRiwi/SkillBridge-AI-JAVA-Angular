// Gestión de empleados (HU-016). Cada empleado es una cuenta de `users` más
// su perfil en `employees` (D-002 en docs/decisiones.md): la cuenta la crea
// UsersService y aquí se le añade el cargo.

import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { User } from '../users/entities/user.entity.js';
import { UserRole } from '../users/enums/index.js';
import { UsersService } from '../users/users.service.js';
import {
  CreateEmployeeDto,
  EmployeeResponseDto,
  UpdateEmployeeDto,
  UpdateEmployeeStatusDto,
} from './dto/index.js';
import { Employee } from './entities/employee.entity.js';
import { EmployeePosition } from './enums/index.js';

// Rol de acceso con el que empieza cada cargo. ADMIN nunca se asigna solo:
// el cambio de rol llega con HU-017 (#87).
const INITIAL_ROLE: Record<EmployeePosition, UserRole> = {
  [EmployeePosition.MANAGER]: UserRole.WAITER,
  [EmployeePosition.WAITER]: UserRole.WAITER,
  [EmployeePosition.COOK]: UserRole.KITCHEN,
  [EmployeePosition.CASHIER]: UserRole.WAITER,
  [EmployeePosition.HOST]: UserRole.WAITER,
};

@Injectable()
export class EmployeesService {
  constructor(
    @InjectRepository(Employee)
    private readonly employeeRepository: Repository<Employee>,
    private readonly usersService: UsersService,
  ) {}

  // Crea la cuenta ACTIVE (RN-097) y el perfil con su cargo (RN-096) en una
  // sola transacción: si algo falla no queda ninguno de los dos.
  async create(dto: CreateEmployeeDto): Promise<EmployeeResponseDto> {
    if (await this.usersService.findByEmail(dto.email)) {
      throw this.duplicatedEmail();
    }

    let employee: Employee;
    try {
      employee = await this.employeeRepository.manager.transaction(
        async (manager) => {
          const user = await this.usersService.create(
            {
              firstName: dto.firstName,
              lastName: dto.lastName,
              email: dto.email,
              phone: dto.phone,
              password: dto.password,
              role: INITIAL_ROLE[dto.position],
            },
            manager,
          );
          const saved = await manager.save(
            manager.create(Employee, {
              userId: user.id,
              position: dto.position,
            }),
          );
          saved.user = user;
          return saved;
        },
      );
    } catch (error) {
      // Dos altas simultáneas con el mismo correo pasan el chequeo de arriba y
      // las frena el índice único de users, que UsersService informa como
      // RN-082. Para un empleado la regla es RN-095: misma respuesta siempre.
      if (
        error instanceof BusinessRuleException &&
        (error.getResponse() as { rule?: string }).rule === 'RN-082'
      ) {
        throw this.duplicatedEmail();
      }
      throw error;
    }

    return EmployeeResponseDto.from(employee);
  }

  async findAll(): Promise<EmployeeResponseDto[]> {
    const employees = await this.employeeRepository.find({
      relations: { user: true },
      order: { createdAt: 'ASC' },
    });
    return employees.map((employee) => EmployeeResponseDto.from(employee));
  }

  async findOne(id: string): Promise<EmployeeResponseDto> {
    return EmployeeResponseDto.from(await this.findEntity(id));
  }

  // Datos personales y cargo. El email, el estado y el rol no se cambian aquí.
  async update(
    id: string,
    dto: UpdateEmployeeDto,
  ): Promise<EmployeeResponseDto> {
    const employee = await this.findEntity(id);
    const { user } = employee;

    user.firstName = dto.firstName ?? user.firstName;
    user.lastName = dto.lastName ?? user.lastName;
    user.phone = dto.phone ?? user.phone;
    employee.position = dto.position ?? employee.position;

    await this.employeeRepository.manager.transaction(async (manager) => {
      await manager.save(User, user);
      await manager.save(Employee, employee);
    });

    return EmployeeResponseDto.from(employee);
  }

  // Activar o desactivar la cuenta. Una cuenta INACTIVE no puede iniciar
  // sesión y sus tokens dejan de valer al momento (RN-098, JwtStrategy).
  async updateStatus(
    id: string,
    dto: UpdateEmployeeStatusDto,
  ): Promise<EmployeeResponseDto> {
    const employee = await this.findEntity(id);
    employee.user.status = dto.status;
    await this.employeeRepository.manager.save(User, employee.user);
    return EmployeeResponseDto.from(employee);
  }

  private duplicatedEmail(): BusinessRuleException {
    return new BusinessRuleException(
      'Ya existe una cuenta con ese correo electrónico',
      'RN-095',
    );
  }

  private async findEntity(id: string): Promise<Employee> {
    const employee = await this.employeeRepository.findOne({
      where: { id },
      relations: { user: true },
    });
    if (!employee) throw new EntityNotFoundException('Employee', id);
    return employee;
  }
}
