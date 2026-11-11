// Perfil del usuario autenticado (HU-019). Siempre trabaja con el id que
// viene del token, nunca con uno enviado por el cliente: así cada usuario
// solo ve y cambia su propia cuenta (RN-107).

import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { EntityNotFoundException } from '../../common/exceptions/index.js';
import { UserResponseDto } from '../users/dto/index.js';
import { User } from '../users/entities/user.entity.js';
import { UpdateProfileDto } from './dto/update-profile.dto.js';

@Injectable()
export class ProfileService {
  constructor(
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
  ) {}

  // UserResponseDto nunca incluye la contraseña ni su hash (RN-108)
  async findMe(userId: string): Promise<UserResponseDto> {
    return UserResponseDto.from(await this.findUser(userId));
  }

  async update(
    userId: string,
    dto: UpdateProfileDto,
  ): Promise<UserResponseDto> {
    const user = await this.findUser(userId);

    user.firstName = dto.firstName ?? user.firstName;
    user.lastName = dto.lastName ?? user.lastName;
    user.phone = dto.phone ?? user.phone;

    return UserResponseDto.from(await this.userRepository.save(user));
  }

  private async findUser(id: string): Promise<User> {
    const user = await this.userRepository.findOneBy({ id });
    if (!user) throw new EntityNotFoundException('User', id);
    return user;
  }
}
