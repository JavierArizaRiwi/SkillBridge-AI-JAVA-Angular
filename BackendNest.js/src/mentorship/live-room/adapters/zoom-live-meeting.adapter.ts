import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import jwt from 'jsonwebtoken';
import { DomainErrors } from '../../../common/domain-error.js';
import type { ZoomConfig } from '../../../config/configuration.js';
import {
  LiveProvider,
  type ProviderRoomResult,
} from '../domain/live-room.types.js';
import type { CreateRoomInput, LiveMeetingPort } from '../ports/live-meeting.port.js';
import { assertProviderOk, httpRequest } from './http-client.js';

interface ZoomMeeting {
  id: number | string;
  join_url: string;
  start_url?: string;
  password?: string;
  agenda?: string;
}

interface ZoomMeetingList {
  meetings?: ZoomMeeting[];
}

@Injectable()
export class ZoomLiveMeetingAdapter implements LiveMeetingPort {
  readonly provider = LiveProvider.ZOOM;

  constructor(private readonly config: ConfigService) {}

  async createRoom(input: CreateRoomInput): Promise<ProviderRoomResult> {
    const existing = await this.findExistingRoom(input.assignment.id);
    if (existing) {
      return existing;
    }

    const token = await this.accessToken();
    const cfg = this.zoom();
    const durationMinutes = Math.max(
      1,
      Math.ceil(
        (input.assignment.accessEndsAt.getTime() -
          input.assignment.accessStartsAt.getTime()) /
          60000,
      ),
    );

    const { status, data } = await httpRequest<ZoomMeeting>({
      method: 'POST',
      url: `${cfg.apiBaseUrl}/users/me/meetings`,
      headers: { authorization: `Bearer ${token}` },
      body: {
        topic: this.topic(input.assignment.id),
        type: 2,
        start_time: input.assignment.accessStartsAt.toISOString(),
        duration: durationMinutes,
        timezone: 'UTC',
        agenda: input.assignment.id,
        settings: {
          waiting_room: true,
          join_before_host: false,
          host_video: true,
          participant_video: true,
        },
      },
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });

    assertProviderOk(status, this.provider, 'no se pudo crear la reunión');

    return this.toResult(data);
  }

  async findExistingRoom(assignmentId: string): Promise<ProviderRoomResult | null> {
    const token = await this.accessToken();
    const cfg = this.zoom();
    const { status, data } = await httpRequest<ZoomMeetingList>({
      method: 'GET',
      url: `${cfg.apiBaseUrl}/users/me/meetings?type=upcoming&page_size=300`,
      headers: { authorization: `Bearer ${token}` },
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });

    assertProviderOk(status, this.provider, 'no se pudo consultar las reuniones');

    const match = (data.meetings ?? []).find((m) => m.agenda === assignmentId);
    return match ? this.toResult(match) : null;
  }

  private toResult(meeting: ZoomMeeting): ProviderRoomResult {
    return {
      externalRoomId: String(meeting.id),
      joinUrl: meeting.join_url,
      hostUrl: meeting.start_url ?? null,
      passcode: meeting.password ?? null,
      hostSignature: this.signature(String(meeting.id), 1),
      inviteUrl: null,
    };
  }

  private signature(meetingNumber: string, role: 0 | 1): string | null {
    const cfg = this.zoom();
    if (!cfg.sdkKey || !cfg.sdkSecret) {
      return null;
    }
    const now = Math.floor(Date.now() / 1000);
    return jwt.sign(
      {
        sdkKey: cfg.sdkKey,
        appKey: cfg.sdkKey,
        mn: meetingNumber,
        role,
        iat: now,
        exp: now + 60 * 60 * 2,
        tokenExp: now + 60 * 60 * 2,
      },
      cfg.sdkSecret,
      { algorithm: 'HS256' },
    );
  }

  private async accessToken(): Promise<string> {
    const cfg = this.zoom();
    if (!cfg.clientId || !cfg.clientSecret || !cfg.accountId) {
      throw DomainErrors.providerUnavailable(
        this.provider,
        'credenciales de Zoom no configuradas',
      );
    }
    const basic = Buffer.from(`${cfg.clientId}:${cfg.clientSecret}`).toString(
      'base64',
    );
    const { status, data } = await httpRequest<{ access_token?: string }>({
      method: 'POST',
      url: `${cfg.oauthUrl}?grant_type=account_credentials&account_id=${encodeURIComponent(cfg.accountId)}`,
      headers: { authorization: `Basic ${basic}` },
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });

    assertProviderOk(status, this.provider, 'no se pudo autenticar en Zoom');
    if (!data.access_token) {
      throw DomainErrors.providerUnavailable(
        this.provider,
        'respuesta de OAuth inválida',
      );
    }
    return data.access_token;
  }

  private topic(assignmentId: string): string {
    return `Mentorship ${assignmentId}`;
  }

  private zoom(): ZoomConfig {
    return this.config.get<ZoomConfig>('zoom') as ZoomConfig;
  }
}
