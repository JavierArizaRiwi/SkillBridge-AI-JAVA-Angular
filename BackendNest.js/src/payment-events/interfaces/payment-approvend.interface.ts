export type MentorshipMode =  'Live' | 'ASYNCHRONOUS';

export interface PaymentApprovedEvent {
    bookingId: string;
    customerId: string;
    customerEmail: string;
    offeringId: string;
    offeringTitle: string;
    providerId: string;
    scheduledAt: string;
    durationHours: number;
    mode: MentorshipMode;
}