// Endpoints del perfil propio (/api/v1/profile, HU-019). Requieren token
// (RN-109) y sirven a cualquier rol.

import { Body, Controller, Get, Patch } from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { CurrentUser } from '../auth/decorators/index.js';
import type { AuthenticatedUser } from '../auth/interfaces/index.js';
import { UserResponseDto } from '../users/dto/index.js';
import { UpdateProfileDto } from './dto/update-profile.dto.js';
import { ProfileService } from './profile.service.js';

@ApiTags('Profile')
@ApiBearerAuth('bearer')
@ApiResponse({ status: 401, description: 'Falta el token' })
@Controller('profile')
export class ProfileController {
  constructor(private readonly profileService: ProfileService) {}

  // GET /api/v1/profile
  @Get()
  @ApiOperation({ summary: 'Consultar mi perfil' })
  @ApiResponse({
    status: 200,
    description: 'Datos de la cuenta autenticada, sin la contraseña',
    type: UserResponseDto,
  })
  getProfile(@CurrentUser() user: AuthenticatedUser): Promise<UserResponseDto> {
    return this.profileService.findMe(user.id);
  }

  // PATCH /api/v1/profile
  @Patch()
  @ApiOperation({
    summary: 'Actualizar mi perfil',
    description:
      'Solo nombre, apellido y teléfono. El correo, el rol, el estado y la contraseña no se cambian aquí (RN-110).',
  })
  @ApiResponse({ status: 200, type: UserResponseDto })
  @ApiResponse({
    status: 400,
    description: 'Datos inválidos o campos no permitidos',
  })
  updateProfile(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: UpdateProfileDto,
  ): Promise<UserResponseDto> {
    return this.profileService.update(user.id, dto);
  }
}
