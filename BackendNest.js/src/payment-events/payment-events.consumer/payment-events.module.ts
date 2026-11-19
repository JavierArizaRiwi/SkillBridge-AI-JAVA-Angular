import { Module } from '@nestjs/common';
import { RabbitmqModule } from '../../rabbitmq/rabbitmq.module.js';
import { PaymentApprovedService } from './payment-approved.service.js';
import {MentorshipModule} from '../../mentorship/mentorship/mentorship.module.js';
@Module({
    imports: [MentorshipModule],
    providers: [PaymentApprovedService],
    exports: [PaymentApprovedService],
})
export class PaymentEventsModule {}
export { MentorshipModule };

