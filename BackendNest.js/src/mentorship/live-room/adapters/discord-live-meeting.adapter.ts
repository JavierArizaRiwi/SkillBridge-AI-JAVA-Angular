import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DomainErrors } from '../../../common/domain-error.js';
import type { DiscordConfig } from '../../../config/configuration.js';
import {
  LiveProvider,
  type ProviderRoomResult,
} from '../domain/live-room.types.js';
import type { CreateRoomInput, LiveMeetingPort } from '../ports/live-meeting.port.js';
import { assertProviderOk, httpRequest } from './http-client.js';

interface DiscordChannel {
  id: string;
  name: string;
  type: number;
}

interface DiscordInvite {
  code: string;
}

@Injectable()
export class DiscordLiveMeetingAdapter implements LiveMeetingPort {
  readonly provider = LiveProvider.DISCORD;

  constructor(private readonly config: ConfigService) {}

  async createRoom(input: CreateRoomInput): Promise<ProviderRoomResult> {
    const existing = await this.findExistingRoom(input.assignment.id);
    if (existing) {
      return existing;
    }

    const cfg = this.discord();
    const channelName = this.channelName(input.assignment.id);

    const channelResponse = await httpRequest<DiscordChannel>({
      method: 'POST',
      url: `${cfg.apiBaseUrl}/guilds/${cfg.guildId}/channels`,
      headers: this.authHeaders(),
      body: {
        name: channelName,
        type: 2,
        parent_id: cfg.categoryId ?? undefined,
      },
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });
    assertProviderOk(
      channelResponse.status,
      this.provider,
      'no se pudo crear el canal de voz',
    );

    const invite = await this.createInvite(
      channelResponse.data.id,
      input.assignment.accessEndsAt,
    );

    return {
      externalRoomId: channelResponse.data.id,
      joinUrl: invite,
      hostUrl: null,
      passcode: null,
      hostSignature: null,
      inviteUrl: invite,
    };
  }

  async findExistingRoom(assignmentId: string): Promise<ProviderRoomResult | null> {
    const cfg = this.discord();
    const channelName = this.channelName(assignmentId);

    const { status, data } = await httpRequest<DiscordChannel[]>({
      method: 'GET',
      url: `${cfg.apiBaseUrl}/guilds/${cfg.guildId}/channels`,
      headers: this.authHeaders(),
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });
    assertProviderOk(status, this.provider, 'no se pudo consultar los canales');

    const channel = (Array.isArray(data) ? data : []).find(
      (item) => item.name === channelName,
    );
    if (!channel) {
      return null;
    }

    const inviteUrl = await this.existingInvite(channel.id);
    if (!inviteUrl) {
      return null;
    }

    return {
      externalRoomId: channel.id,
      joinUrl: inviteUrl,
      hostUrl: null,
      passcode: null,
      hostSignature: null,
      inviteUrl,
    };
  }

  private async createInvite(
    channelId: string,
    accessEndsAt: Date,
  ): Promise<string> {
    const cfg = this.discord();
    const maxAge = Math.max(
      60,
      Math.floor((accessEndsAt.getTime() - Date.now()) / 1000),
    );

    const { status, data } = await httpRequest<DiscordInvite>({
      method: 'POST',
      url: `${cfg.apiBaseUrl}/channels/${channelId}/invites`,
      headers: this.authHeaders(),
      body: { max_age: maxAge, max_uses: 0, unique: true },
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });
    assertProviderOk(status, this.provider, 'no se pudo crear la invitación');

    return `https://discord.gg/${data.code}`;
  }

  private async existingInvite(channelId: string): Promise<string | null> {
    const cfg = this.discord();
    const { status, data } = await httpRequest<DiscordInvite[]>({
      method: 'GET',
      url: `${cfg.apiBaseUrl}/channels/${channelId}/invites`,
      headers: this.authHeaders(),
      timeoutMs: cfg.timeoutMs,
      provider: this.provider,
    });
    assertProviderOk(status, this.provider, 'no se pudo consultar la invitación');

    const invite = Array.isArray(data) ? data[0] : undefined;
    return invite ? `https://discord.gg/${invite.code}` : null;
  }

  private channelName(assignmentId: string): string {
    return `mentor-${assignmentId.slice(0, 8)}`;
  }

  private authHeaders(): Record<string, string> {
    const cfg = this.discord();
    if (!cfg.botToken) {
      throw DomainErrors.providerUnavailable(
        this.provider,
        'token de bot no configurado',
      );
    }
    if (!cfg.guildId) {
      throw DomainErrors.providerUnavailable(
        this.provider,
        'guild de Discord no configurada',
      );
    }
    return { authorization: `Bot ${cfg.botToken}` };
  }

  private discord(): DiscordConfig {
    return this.config.get<DiscordConfig>('discord') as DiscordConfig;
  }
}
