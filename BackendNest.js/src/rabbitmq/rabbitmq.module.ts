
import { Global, Module } from '@nestjs/common';

import { RabbitmqService } from './rabbitmq.service.js';

@Global()
@Module({
    providers: [RabbitmqService],
    exports: [RabbitmqService],
})
export class RabbitmqModule {}
