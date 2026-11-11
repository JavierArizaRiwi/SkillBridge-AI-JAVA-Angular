import { ApiProperty } from '@nestjs/swagger';
import { UserRole, UserStatus } from '../../users/enums/index.js';
import { Employee } from '../entities/employee.entity.js';
import { EmployeePosition } from '../enums/index.js';

// Lo que la API muestra de un empleado: su perfil junto con los datos de su
// cuenta, nunca la contraseña. Se construye campo a campo, igual que
// UserResponseDto, para no publicar un campo nuevo sin querer.
export class EmployeeResponseDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ format: 'uuid', description: 'Cuenta del empleado' })
  userId: string;

  @ApiProperty({ example: 'Ana' })
  firstName: string;

  @ApiProperty({ example: 'López' })
  lastName: string;

  @ApiProperty({ example: 'ana.lopez@zerobug.dev' })
  email: string;

  @ApiProperty({ example: '+57 300 123 4567' })
  phone: string;

  @ApiProperty({ enum: EmployeePosition, example: EmployeePosition.WAITER })
  position: EmployeePosition;

  @ApiProperty({ enum: UserRole, example: UserRole.WAITER })
  role: UserRole;

  @ApiProperty({ enum: UserStatus, example: UserStatus.ACTIVE })
  status: UserStatus;

  @ApiProperty()
  createdAt: Date;

  @ApiProperty()
  updatedAt: Date;

  // Necesita el empleado cargado con su relación `user`
  static from(employee: Employee): EmployeeResponseDto {
    const dto = new EmployeeResponseDto();
    dto.id = employee.id;
    dto.userId = employee.userId;
    dto.firstName = employee.user.firstName;
    dto.lastName = employee.user.lastName;
    dto.email = employee.user.email;
    dto.phone = employee.user.phone;
    dto.position = employee.position;
    dto.role = employee.user.role;
    dto.status = employee.user.status;
    dto.createdAt = employee.createdAt;
    dto.updatedAt = employee.updatedAt;
    return dto;
  }
}
