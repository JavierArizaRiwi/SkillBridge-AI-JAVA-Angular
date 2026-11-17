import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import jwt from 'jsonwebtoken';
import { DomainErrors } from '../../../common/domain-error.js';
import type { GoogleConfig } from '../../../config/configuration.js';
import {
  LiveProvider,
  type ProviderRoomResult,
} from '../domain/live-room.types.js';
import type { CreateRoomInput, LiveMeetingPort } from '../ports/live-meeting.port.js';
import { assertProviderOk, httpRequest } from './http-client.js';

interface GoogleEvent {
  id: string;
  hangoutLink?: string;
  conferenceData?: {
    entryPoints?: Array<{ entryPointType?: string; uri?: string }>;
  };
}

interface GoogleEventList {
  items?: GoogleEvent[];
}

const CALENDAR_SCOPE = 'https://www.googleapis.com/auth/calendar';

@Injectable()
export class GoogleMeetLiveMeetingAdapter implements LiveMeetingPort {
  readonly provider = LiveProvider.MEET;

  constructor(private readonly config: ConfigService) {}

  async createRoom(input: CreateRoomInput): Promise<ProviderRoomResult> {
    const existing = await this.findExistingRoom(input.assignment.id);
    if (existing) {
      return existing;
    }

    const token = await this.accessToken();
    const cfg = this.google();
    const calendar = encodeURIComponent(cfg.calendarId ?? 'primary');

    const { status, data } = await httpRequest<GoogleEvent>({
      method: 'POST',
      url: `${cfg.apiBaseUrl}/calendars/${calendar}/events?conferenceDataVersion=1`,
      headers: { authorization: `Bearer ${token}` },
      body: {
        summary: `Mentorship ${input.assignment.id}`,
        start: { dateTime: input.assignment.accessStartsAt.toISOString() },
        end: { dateTime: input.assignment.accessEndsAt.toISOString() },
        conferenceData: {
          createRequest: {
            requestId: input.assignment.id,
            conferenceSolutionKey: { type: 'hangoutsMeet' },
          },
        },
        extendedProperties: { private: { assignmentId: input.assignment.id } },
      },
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });

    assertProviderOk(status, this.provider, 'no se pudo crear el evento');

    return this.toResult(data);
  }

  async findExistingRoom(assignmentId: string): Promise<ProviderRoomResult | null> {
    const token = await this.accessToken();
    const cfg = this.google();
    const calendar = encodeURIComponent(cfg.calendarId ?? 'primary');

    const { status, data } = await httpRequest<GoogleEventList>({
      method: 'GET',
      url: `${cfg.apiBaseUrl}/calendars/${calendar}/events?privateExtendedProperty=assignmentId%3D${encodeURIComponent(assignmentId)}`,
      headers: { authorization: `Bearer ${token}` },
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });

    assertProviderOk(status, this.provider, 'no se pudo consultar el calendario');

    const match = data.items?.[0];
    return match ? this.toResult(match) : null;
  }

  private toResult(event: GoogleEvent): ProviderRoomResult {
    const videoEntry = event.conferenceData?.entryPoints?.find(
      (entry) => entry.entryPointType === 'video',
    );
    const joinUrl = event.hangoutLink ?? videoEntry?.uri ?? '';
    return {
      externalRoomId: event.id,
      joinUrl,
      hostUrl: joinUrl,
      passcode: null,
      hostSignature: null,
      inviteUrl: null,
    };
  }

  private async accessToken(): Promise<string> {
    const cfg = this.google();
    if (!cfg.clientEmail || !cfg.privateKey) {
      throw DomainErrors.providerUnavailable(
        this.provider,
        'credenciales de Google no configuradas',
      );
    }

    const now = Math.floor(Date.now() / 1000);
    const assertion = jwt.sign(
      {
        iss: cfg.clientEmail,
        scope: CALENDAR_SCOPE,
        aud: cfg.oauthUrl,
        iat: now,
        exp: now + 3600,
      },
      cfg.privateKey,
      { algorithm: 'RS256' },
    );

    const form = new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    });

    const { status, data } = await httpRequest<{ access_token?: string }>({
      method: 'POST',
      url: cfg.oauthUrl,
      form,
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });

    assertProviderOk(status, this.provider, 'no se pudo autenticar en Google');
    if (!data.access_token) {
      throw DomainErrors.providerUnavailable(
        this.provider,
        'respuesta de OAuth inválida',
      );
    }
    return data.access_token;
  }

  private google(): GoogleConfig {
    return this.config.get<GoogleConfig>('google') as GoogleConfig;
  }
}
