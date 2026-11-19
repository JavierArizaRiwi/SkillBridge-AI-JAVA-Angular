
import { Injectable } from '@nestjs/common';

@Injectable()
export class AppService {
  getHello(): string {
    return 'SkillBridge API funcionando correctamente';
  }

  getHealth() {
    return {
      status: 'ok',
      application: 'SkillBridge',
      service: 'BackendNest',
      timestamp: new Date().toISOString(),
    };
  }
}
