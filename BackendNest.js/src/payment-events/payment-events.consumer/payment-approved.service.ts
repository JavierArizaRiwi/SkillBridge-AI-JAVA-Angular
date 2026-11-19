import {Injectable, Logger} from '@nestjs/common';

import {MentorshipService} from '../../mentorship/mentorship/mentorship.service.js';
import {PaymentApprovedEvent} from '../interfaces/payment-approvend.interface.js';

@Injectable()
export class PaymentApprovedService {
    private readonly logger = new Logger(PaymentApprovedService.name);

    constructor(private readonly mentorshipService: MentorshipService,) {}
    
    
    async process(event: PaymentApprovedEvent): Promise<void> {
        const assignmet = await this.mentorshipService.createFromPayment(event);

        this.logger.log(
            `Mentoria procesada para booking: ${assignmet.bookingId}`,
        );
        
    }

}
