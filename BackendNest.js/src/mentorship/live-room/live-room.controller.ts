import {
  Body,
  Controller,
  Get,
  Headers,
  Param,
  Post,
  Res,
  UseGuards,
} from '@nestjs/common';
import type { Response } from 'express';
import type { AuthenticatedUser } from '../../auth/auth.types.js';
import { CurrentUser } from '../../auth/current-user.decorator.js';
import { JwtAuthGuard } from '../../auth/jwt-auth.guard.js';
import { Roles } from '../../auth/roles.decorator.js';
import { RolesGuard } from '../../auth/roles.guard.js';
import { CreateLiveRoomDto } from './dto/create-live-room.dto.js';
import { LiveRoomService } from './live-room.service.js';

@Controller('api/v1/mentorships')
@UseGuards(JwtAuthGuard, RolesGuard)
export class LiveRoomController {
  constructor(private readonly service: LiveRoomService) {}

  @Post(':id/live-room')
  @Roles('PROVIDER', 'ADMIN')
  async create(
    @Param('id') id: string,
    @Body() dto: CreateLiveRoomDto,
    @CurrentUser() user: AuthenticatedUser,
    @Headers('x-correlation-id') correlationId: string | undefined,
    @Res({ passthrough: true }) response: Response,
  ) {
    const { access, created } = await this.service.createRoom(
      id,
      dto.provider,
      user,
      correlationId ?? null,
    );
    response.status(created ? 201 : 200);
    return access;
  }

  @Get(':id/access')
  @Roles('CUSTOMER', 'PROVIDER', 'ADMIN')
  access(@Param('id') id: string, @CurrentUser() user: AuthenticatedUser) {
    return this.service.getAccess(id, user);
  }
}
