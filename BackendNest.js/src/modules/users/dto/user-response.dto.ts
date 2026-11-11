import { ApiProperty } from '@nestjs/swagger';
import { User } from '../entities/user.entity.js';
import { UserRole, UserStatus } from '../enums/index.js';

// Lo que la API muestra de un usuario: nunca la contraseña ni su hash
// (RN-087, RN-093, RN-108). Se construye campo a campo para que un campo
// nuevo de la entidad no se publique sin querer.
export class UserResponseDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ example: 'Carlos' })
  firstName: string;

  @ApiProperty({ example: 'Pérez' })
  lastName: string;

  @ApiProperty({ example: 'carlos@example.com' })
  email: string;

  @ApiProperty({ example: '+57 300 123 4567' })
  phone: string;

  @ApiProperty({ enum: UserRole, example: UserRole.CUSTOMER })
  role: UserRole;

  @ApiProperty({ enum: UserStatus, example: UserStatus.ACTIVE })
  status: UserStatus;

  @ApiProperty()
  createdAt: Date;

  static from(user: User): UserResponseDto {
    const dto = new UserResponseDto();
    dto.id = user.id;
    dto.firstName = user.firstName;
    dto.lastName = user.lastName;
    dto.email = user.email;
    dto.phone = user.phone;
    dto.role = user.role;
    dto.status = user.status;
    dto.createdAt = user.createdAt;
    return dto;
  }
}
