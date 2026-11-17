import { ConfigService } from '@nestjs/config';
import { DomainError } from '../src/common/domain-error.js';
import { DiscordLiveMeetingAdapter } from '../src/mentorship/live-room/adapters/discord-live-meeting.adapter.js';
import { GoogleMeetLiveMeetingAdapter } from '../src/mentorship/live-room/adapters/google-meet-live-meeting.adapter.js';
import { ZoomLiveMeetingAdapter } from '../src/mentorship/live-room/adapters/zoom-live-meeting.adapter.js';
import { createAssignment } from './helpers/live-room-fixtures.js';
import {
  type FakeServer,
  json,
  startFakeServer,
} from './helpers/fake-http-server.js';
import { getTestKeys } from './helpers/test-keys.js';

function configService(config: Record<string, unknown>): ConfigService {
  return {
    get: (key: string) => config[key],
  } as unknown as ConfigService;
}

async function expectDomainCode(
  promise: Promise<unknown>,
  code: string,
): Promise<void> {
  await expect(promise).rejects.toMatchObject({ code } as Partial<DomainError>);
}

describe('ZoomLiveMeetingAdapter', () => {
  let server: FakeServer;

  afterEach(async () => {
    await server?.close();
  });

  it('crea la reunión con OAuth, URL de anfitrión y firma de rol', async () => {
    server = await startFakeServer((req, res) => {
      if (req.url?.startsWith('/oauth/token')) {
        return json(res, 200, { access_token: 'zoom-token' });
      }
      if (req.method === 'GET' && req.url?.includes('/users/me/meetings')) {
        return json(res, 200, { meetings: [] });
      }
      if (req.method === 'POST' && req.url === '/api/users/me/meetings') {
        return json(res, 201, {
          id: 999,
          join_url: 'https://zoom.example/j/999',
          start_url: 'https://zoom.example/s/999',
          password: 'pass-999',
        });
      }
      return json(res, 404, {});
    });

    const adapter = new ZoomLiveMeetingAdapter(
      configService({
        zoom: {
          clientId: 'cid',
          clientSecret: 'secret',
          accountId: 'acc',
          sdkKey: 'sdk',
          sdkSecret: 'sdk-secret',
          apiBaseUrl: `${server.url}/api`,
          oauthUrl: `${server.url}/oauth/token`,
          timeoutMs: 2000,
        },
      }),
    );

    const result = await adapter.createRoom({ assignment: createAssignment() });
    expect(result.joinUrl).toBe('https://zoom.example/j/999');
    expect(result.hostUrl).toBe('https://zoom.example/s/999');
    expect(result.passcode).toBe('pass-999');
    expect(result.hostSignature).toBeTruthy();
  });

  it('reconcilia una reunión existente sin crear otra', async () => {
    const assignment = createAssignment();
    server = await startFakeServer((req, res) => {
      if (req.url?.startsWith('/oauth/token')) {
        return json(res, 200, { access_token: 'zoom-token' });
      }
      if (req.method === 'GET' && req.url?.includes('/users/me/meetings')) {
        return json(res, 200, {
          meetings: [
            {
              id: 555,
              join_url: 'https://zoom.example/j/555',
              start_url: 'https://zoom.example/s/555',
              password: 'p',
              agenda: assignment.id,
            },
          ],
        });
      }
      return json(res, 404, {});
    });

    const adapter = new ZoomLiveMeetingAdapter(
      configService({
        zoom: {
          clientId: 'cid',
          clientSecret: 'secret',
          accountId: 'acc',
          apiBaseUrl: `${server.url}/api`,
          oauthUrl: `${server.url}/oauth/token`,
          timeoutMs: 2000,
        },
      }),
    );

    const result = await adapter.createRoom({ assignment });
    expect(result.externalRoomId).toBe('555');
    const created = server.requests.filter(
      (r) => r.method === 'POST' && r.url === '/api/users/me/meetings',
    );
    expect(created).toHaveLength(0);
  });

  it('falla con 503 si faltan credenciales', async () => {
    const adapter = new ZoomLiveMeetingAdapter(
      configService({
        zoom: {
          apiBaseUrl: 'http://127.0.0.1:1',
          oauthUrl: 'http://127.0.0.1:1',
          timeoutMs: 200,
        },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_UNAVAILABLE',
    );
  });

  it('mapea el timeout a 504', async () => {
    server = await startFakeServer((_req, res) => {
      setTimeout(() => json(res, 200, { access_token: 'late' }), 400);
    });
    const adapter = new ZoomLiveMeetingAdapter(
      configService({
        zoom: {
          clientId: 'cid',
          clientSecret: 'secret',
          accountId: 'acc',
          apiBaseUrl: `${server.url}/api`,
          oauthUrl: `${server.url}/oauth/token`,
          timeoutMs: 100,
        },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_TIMEOUT',
    );
  });
});

describe('GoogleMeetLiveMeetingAdapter', () => {
  let server: FakeServer;

  afterEach(async () => {
    await server?.close();
  });

  it('crea un evento con conferenceData y enlace de Meet', async () => {
    server = await startFakeServer((req, res) => {
      if (req.url === '/oauth2') {
        return json(res, 200, { access_token: 'google-token' });
      }
      if (req.method === 'GET' && req.url?.includes('/events')) {
        return json(res, 200, { items: [] });
      }
      if (req.method === 'POST' && req.url?.includes('/events')) {
        return json(res, 201, {
          id: 'event-1',
          hangoutLink: 'https://meet.google.com/abc-defg-hij',
          conferenceData: {
            entryPoints: [
              {
                entryPointType: 'video',
                uri: 'https://meet.google.com/abc-defg-hij',
              },
            ],
          },
        });
      }
      return json(res, 404, {});
    });

    const adapter = new GoogleMeetLiveMeetingAdapter(
      configService({
        google: {
          clientEmail: 'svc@example.iam.gserviceaccount.com',
          privateKey: getTestKeys().privateKey,
          calendarId: 'primary',
          apiBaseUrl: `${server.url}/calendar`,
          oauthUrl: `${server.url}/oauth2`,
          timeoutMs: 2000,
        },
      }),
    );

    const result = await adapter.createRoom({ assignment: createAssignment() });
    expect(result.externalRoomId).toBe('event-1');
    expect(result.joinUrl).toBe('https://meet.google.com/abc-defg-hij');
    const createRequest = server.requests.find(
      (r) => r.method === 'POST' && r.url?.includes('conferenceDataVersion=1'),
    );
    expect(createRequest?.body).toContain('hangoutsMeet');
  });

  it('falla con 503 si no está configurado', async () => {
    const adapter = new GoogleMeetLiveMeetingAdapter(
      configService({
        google: {
          apiBaseUrl: 'http://127.0.0.1:1',
          oauthUrl: 'http://127.0.0.1:1',
          timeoutMs: 200,
        },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_UNAVAILABLE',
    );
  });

  it('mapea el timeout a 504', async () => {
    server = await startFakeServer((_req, res) => {
      setTimeout(() => json(res, 200, { access_token: 'late' }), 400);
    });
    const adapter = new GoogleMeetLiveMeetingAdapter(
      configService({
        google: {
          clientEmail: 'svc@example.iam.gserviceaccount.com',
          privateKey: getTestKeys().privateKey,
          calendarId: 'primary',
          apiBaseUrl: `${server.url}/calendar`,
          oauthUrl: `${server.url}/oauth2`,
          timeoutMs: 100,
        },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_TIMEOUT',
    );
  });

  it('mapea el límite de tasa a 503', async () => {
    server = await startFakeServer((req, res) => {
      if (req.url === '/oauth2') {
        return json(res, 200, { access_token: 'google-token' });
      }
      return json(res, 429, { error: 'rate limited' });
    });
    const adapter = new GoogleMeetLiveMeetingAdapter(
      configService({
        google: {
          clientEmail: 'svc@example.iam.gserviceaccount.com',
          privateKey: getTestKeys().privateKey,
          calendarId: 'primary',
          apiBaseUrl: `${server.url}/calendar`,
          oauthUrl: `${server.url}/oauth2`,
          timeoutMs: 2000,
        },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_UNAVAILABLE',
    );
  });
});

describe('DiscordLiveMeetingAdapter', () => {
  let server: FakeServer;

  afterEach(async () => {
    await server?.close();
  });

  it('crea canal de voz e invitación con expiración', async () => {
    server = await startFakeServer((req, res) => {
      if (req.method === 'GET' && req.url === '/guilds/g/channels') {
        return json(res, 200, []);
      }
      if (req.method === 'POST' && req.url === '/guilds/g/channels') {
        return json(res, 201, { id: 'chan-1', name: 'mentor-abc', type: 2 });
      }
      if (req.method === 'POST' && req.url === '/channels/chan-1/invites') {
        return json(res, 201, { code: 'invite-code' });
      }
      return json(res, 404, {});
    });

    const adapter = new DiscordLiveMeetingAdapter(
      configService({
        discord: {
          botToken: 'bot-token',
          guildId: 'g',
          categoryId: 'cat',
          apiBaseUrl: server.url,
          timeoutMs: 2000,
        },
      }),
    );

    const result = await adapter.createRoom({ assignment: createAssignment() });
    expect(result.joinUrl).toBe('https://discord.gg/invite-code');
    const invite = server.requests.find((r) =>
      r.url?.includes('/channels/chan-1/invites'),
    );
    expect(invite?.body).toContain('max_age');
  });

  it('reutiliza el canal y la invitación existentes', async () => {
    const assignment = createAssignment();
    const channelName = `mentor-${assignment.id.slice(0, 8)}`;
    server = await startFakeServer((req, res) => {
      if (req.method === 'GET' && req.url === '/guilds/g/channels') {
        return json(res, 200, [{ id: 'chan-9', name: channelName, type: 2 }]);
      }
      if (req.method === 'GET' && req.url === '/channels/chan-9/invites') {
        return json(res, 200, [{ code: 'existing' }]);
      }
      return json(res, 404, {});
    });

    const adapter = new DiscordLiveMeetingAdapter(
      configService({
        discord: {
          botToken: 'bot-token',
          guildId: 'g',
          apiBaseUrl: server.url,
          timeoutMs: 2000,
        },
      }),
    );

    const result = await adapter.createRoom({ assignment });
    expect(result.externalRoomId).toBe('chan-9');
    expect(result.joinUrl).toBe('https://discord.gg/existing');
    expect(
      server.requests.filter((r) => r.method === 'POST'),
    ).toHaveLength(0);
  });

  it('falla con 503 si falta el token del bot', async () => {
    const adapter = new DiscordLiveMeetingAdapter(
      configService({
        discord: { apiBaseUrl: 'http://127.0.0.1:1', timeoutMs: 200 },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_UNAVAILABLE',
    );
  });

  it('mapea el timeout a 504', async () => {
    server = await startFakeServer((_req, res) => {
      setTimeout(() => json(res, 200, []), 400);
    });
    const adapter = new DiscordLiveMeetingAdapter(
      configService({
        discord: {
          botToken: 'bot-token',
          guildId: 'g',
          apiBaseUrl: server.url,
          timeoutMs: 100,
        },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_TIMEOUT',
    );
  });

  it('mapea el error del servidor a 503', async () => {
    server = await startFakeServer((_req, res) => json(res, 500, {}));
    const adapter = new DiscordLiveMeetingAdapter(
      configService({
        discord: {
          botToken: 'bot-token',
          guildId: 'g',
          apiBaseUrl: server.url,
          timeoutMs: 2000,
        },
      }),
    );
    await expectDomainCode(
      adapter.createRoom({ assignment: createAssignment() }),
      'LIVE_PROVIDER_UNAVAILABLE',
    );
  });
});
