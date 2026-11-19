import {
    IsEmail,
    IsEnum,
    IsISO8601,
    IsNotEmpty,
    IsNumber,
    IsPositive,
    IsString,
    IsUUID,
} from 'class-validator';

export enum PaymentStatus {
    Live = 'live',
    Asynchronous = 'asynchronous',
}

export enum MentorshipModeDto {
    Live = 'live',
    Asynchronous = 'asynchronous',
}

export class PaymentApprovedDto {
    @IsUUID()
    bookingId: string;

    @IsUUID()
    customerId: string;

    @IsEmail()
    customerEmail: string;

    @IsUUID()
    offeringId: string;

    @IsString()
    @IsNotEmpty()
    offeringTitle: string;

    @IsUUID()
    providerId: string;

    @IsISO8601()
    scheduledAt: string;

    @IsNumber()
    @IsPositive()
    durationHours: number;

    @IsEnum(MentorshipModeDto)
    mode: MentorshipModeDto;
}