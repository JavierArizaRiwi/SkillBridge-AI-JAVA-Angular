import { Controller, Get } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { Public } from '../auth/decorators/index.js';

@ApiTags('Health')
@Public()
@Controller('health')
export class HealthController {
  @Get()
  getHealth() {
    return { status: 'ok', service: 'restaurant-api' };
  }
}
