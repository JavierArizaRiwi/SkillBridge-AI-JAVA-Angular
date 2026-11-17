import type { AuthenticatedUser } from '../src/auth/auth.types.js';
import { DomainError, DomainErrors } from '../src/common/domain-error.js';
import {
  LiveProvider,
  MentorshipMode,
  MentorshipStatus,
} from '../src/mentorship/live-room/domain/live-room.types.js';
import { LiveRoomMetrics } from '../src/mentorship/live-room/live-room.metrics.js';
import type { LiveMeetingPort } from '../src/mentorship/live-room/ports/live-meeting.port.js';
import {
  FakeLiveMeetingAdapter,
  FakeLiveRoomRepository,
  createAssignment,
  createCipher,
  createService,
} from './helpers/live-room-fixtures.js';

const provider: AuthenticatedUser = {
  uid: 'provider-1',
  email: 'provider@example.com',
  role: 'PROVIDER',
};
const customer: AuthenticatedUser = {
  uid: 'customer-1',
  email: 'customer@example.com',
  role: 'CUSTOMER',
};
const admin: AuthenticatedUser = {
  uid: 'admin-1',
  email: 'admin@example.com',
  role: 'ADMIN',
};

function build(
  options: { providerId?: string; zoomFailure?: () => Error } = {},
) {
  const repository = new FakeLiveRoomRepository();
  const zoom = new FakeLiveMeetingAdapter(
    LiveProvider.ZOOM,
    {
      externalRoomId: '999',
      joinUrl: 'https://zoom.example/j/999',
      hostUrl: 'https://zoom.example/s/999',
      passcode: 'secret-pass',
      hostSignature: 'host-signature',
    },
    options.zoomFailure,
  );
  const meet = new FakeLiveMeetingAdapter(LiveProvider.MEET, {
    externalRoomId: 'event-1',
    joinUrl: 'https://meet.google.com/abc-defg-hij',
    hostUrl: 'https://meet.google.com/abc-defg-hij',
  });
  const discord = new FakeLiveMeetingAdapter(LiveProvider.DISCORD, {
    externalRoomId: 'channel-1',
    joinUrl: 'https://discord.gg/abc',
    inviteUrl: 'https://discord.gg/abc',
  });
  const adapters = new Map<LiveProvider, LiveMeetingPort>([
    [LiveProvider.ZOOM, zoom],
    [LiveProvider.MEET, meet],
    [LiveProvider.DISCORD, discord],
  ]);
  repository.addAssignment(
    createAssignment(options.providerId ? { providerId: options.providerId } : {}),
  );
  const metrics = new LiveRoomMetrics();
  return {
    repository,
    zoom,
    meet,
    discord,
    metrics,
    service: createService(repository, adapters, metrics),
  };
}

async function expectDomainError(
  promise: Promise<unknown>,
  code: string,
): Promise<DomainError> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(DomainError);
    const domainError = error as DomainError;
    expect(domainError.code).toBe(code);
    return domainError;
  }
  throw new Error(`Se esperaba un DomainError con código ${code}`);
}

describe('LiveRoomService.createRoom', () => {
  it('crea la sala, responde como anfitrión y registra el outbox', async () => {
    const { service, repository, zoom } = build();
    const result = await service.createRoom(
      createAssignment().id,
      LiveProvider.ZOOM,
      provider,
      'corr-1',
    );

    expect(result.created).toBe(true);
    expect(result.access.hostUrl).toBe('https://zoom.example/s/999');
    expect(result.access.hostSignature).toBe('host-signature');
    expect(zoom.calls).toBe(1);
    expect(repository.outbox).toHaveLength(1);
    expect(repository.outbox[0].eventType).toBe('MentorshipLiveRoomCreated');
    expect(repository.outbox[0].payload).toEqual({
      assignmentId: createAssignment().id,
      provider: 'ZOOM',
      fallbackUsed: false,
    });
    expect(repository.outbox[0].correlationId).toBe('corr-1');
  });

  it('es idempotente: la repetición no vuelve a llamar al proveedor', async () => {
    const { service, zoom } = build();
    const id = createAssignment().id;
    const first = await service.createRoom(id, LiveProvider.ZOOM, provider, null);
    const second = await service.createRoom(id, LiveProvider.ZOOM, provider, null);

    expect(first.created).toBe(true);
    expect(second.created).toBe(false);
    expect(zoom.calls).toBe(1);
  });

  it('rechaza ASYNCHRONOUS sin llamar al proveedor', async () => {
    const { service, repository, zoom } = build();
    const assignment = createAssignment({ mode: MentorshipMode.ASYNCHRONOUS });
    repository.assignments.set(assignment.id, assignment);

    await expectDomainError(
      service.createRoom(assignment.id, LiveProvider.ZOOM, provider, null),
      'LIVE_ROOM_NOT_APPLICABLE',
    );
    expect(zoom.calls).toBe(0);
  });

  it('rechaza estados terminales', async () => {
    const { service, repository } = build();
    const assignment = createAssignment({ status: MentorshipStatus.COMPLETED });
    repository.assignments.set(assignment.id, assignment);

    await expectDomainError(
      service.createRoom(assignment.id, LiveProvider.ZOOM, provider, null),
      'INVALID_STATE_TRANSITION',
    );
  });

  it('oculta la asignación de otro Provider con 404', async () => {
    const { service } = build({ providerId: 'other-provider' });
    await expectDomainError(
      service.createRoom(createAssignment().id, LiveProvider.ZOOM, provider, null),
      'NOT_FOUND',
    );
  });

  it('rechaza a un CUSTOMER con 403', async () => {
    const { service } = build();
    await expectDomainError(
      service.createRoom(createAssignment().id, LiveProvider.ZOOM, customer, null),
      'FORBIDDEN',
    );
  });

  it('rechaza CUSTOM_URL como proveedor no soportado', async () => {
    const { service } = build();
    await expectDomainError(
      service.createRoom(
        createAssignment().id,
        LiveProvider.CUSTOM_URL,
        provider,
        null,
      ),
      'UNSUPPORTED_LIVE_PROVIDER',
    );
  });

  it('rechaza cambiar de proveedor cuando ya existe sala', async () => {
    const { service } = build();
    const id = createAssignment().id;
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);
    await expectDomainError(
      service.createRoom(id, LiveProvider.MEET, provider, null),
      'LIVE_ROOM_ALREADY_EXISTS',
    );
  });

  it('serializa solicitudes concurrentes en una sola llamada externa', async () => {
    const { service, repository, zoom } = build();
    const id = createAssignment().id;

    const results = await Promise.all(
      Array.from({ length: 10 }, () =>
        service.createRoom(id, LiveProvider.ZOOM, provider, null),
      ),
    );

    expect(zoom.calls).toBe(1);
    expect(repository.rooms.size).toBe(1);
    expect(repository.outbox).toHaveLength(1);
    expect(results.filter((r) => r.created)).toHaveLength(1);
  });

  it('no deja estado inconsistente si falla la persistencia (FR-016)', async () => {
    const { service, repository, zoom } = build();
    const id = createAssignment().id;
    repository.failNextSave = true;

    await expect(
      service.createRoom(id, LiveProvider.ZOOM, provider, null),
    ).rejects.toThrow('fallo simulado al persistir la sala');

    expect(repository.rooms.size).toBe(0);
    expect(repository.outbox).toHaveLength(0);

    const retry = await service.createRoom(id, LiveProvider.ZOOM, provider, null);
    expect(retry.created).toBe(true);
    expect(repository.rooms.size).toBe(1);
    expect(zoom.calls).toBe(2);
  });
});

describe('LiveRoomMetrics (NFR-003)', () => {
  it('cuenta intentos y éxitos por proveedor', async () => {
    const { service, metrics } = build();
    const id = createAssignment().id;
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);

    expect(metrics.snapshot().ZOOM).toMatchObject({
      attempts: 1,
      successes: 1,
      failures: 0,
      timeouts: 0,
    });
  });

  it('no cuenta intentos en respuestas idempotentes', async () => {
    const { service, metrics } = build();
    const id = createAssignment().id;
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);

    expect(metrics.snapshot().ZOOM.attempts).toBe(1);
    expect(metrics.snapshot().ZOOM.successes).toBe(1);
  });

  it('clasifica fallos de proveedor', async () => {
    const { service, metrics } = build({
      zoomFailure: () => DomainErrors.providerUnavailable('ZOOM'),
    });
    await expectDomainError(
      service.createRoom(createAssignment().id, LiveProvider.ZOOM, provider, null),
      'LIVE_PROVIDER_UNAVAILABLE',
    );

    expect(metrics.snapshot().ZOOM).toMatchObject({
      attempts: 1,
      failures: 1,
      timeouts: 0,
    });
  });

  it('clasifica timeouts de proveedor', async () => {
    const { service, metrics } = build({
      zoomFailure: () => DomainErrors.providerTimeout('ZOOM'),
    });
    await expectDomainError(
      service.createRoom(createAssignment().id, LiveProvider.ZOOM, provider, null),
      'LIVE_PROVIDER_TIMEOUT',
    );

    expect(metrics.snapshot().ZOOM).toMatchObject({
      attempts: 1,
      failures: 0,
      timeouts: 1,
    });
  });
});

describe('Redacción de secretos (FR-012)', () => {
  it.each([
    [LiveProvider.ZOOM],
    [LiveProvider.MEET],
    [LiveProvider.DISCORD],
  ])('no expone secretos de anfitrión al Customer (%s)', async (provider_) => {
    const { service, repository } = build();
    const id = createAssignment().id;
    await service.createRoom(id, provider_, provider, null);

    const access = await service.getAccess(id, customer);
    const serialized = JSON.stringify(access);

    expect('hostUrl' in access).toBe(false);
    expect('hostSignature' in access).toBe(false);
    expect(serialized).not.toContain('host-signature');
    expect(repository.outbox[0].payload).toEqual({
      assignmentId: id,
      provider: provider_,
      fallbackUsed: false,
    });
    expect(Object.keys(repository.outbox[0].payload).sort()).toEqual([
      'assignmentId',
      'fallbackUsed',
      'provider',
    ]);
  });
});

describe('LiveRoomService.getAccess', () => {
  it('entrega datos de participante al Customer, sin secretos de anfitrión', async () => {
    const { service } = build();
    const id = createAssignment().id;
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);

    const access = await service.getAccess(id, customer);
    expect(access).toMatchObject({
      provider: LiveProvider.ZOOM,
      joinUrl: 'https://zoom.example/j/999',
      passcode: 'secret-pass',
    });
    expect('hostUrl' in access).toBe(false);
    expect('hostSignature' in access).toBe(false);
  });

  it('entrega datos de anfitrión al Provider dueño', async () => {
    const { service } = build();
    const id = createAssignment().id;
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);

    const access = await service.getAccess(id, provider);
    expect(access).toMatchObject({
      hostUrl: 'https://zoom.example/s/999',
      hostSignature: 'host-signature',
    });
  });

  it('rechaza fuera de la ventana de acceso', async () => {
    const { service, repository } = build();
    const assignment = createAssignment({
      accessStartsAt: new Date(Date.now() + 3_600_000),
      accessEndsAt: new Date(Date.now() + 7_200_000),
    });
    repository.assignments.set(assignment.id, assignment);
    await service.createRoom(assignment.id, LiveProvider.ZOOM, provider, null);

    await expectDomainError(
      service.getAccess(assignment.id, customer),
      'OUTSIDE_ACCESS_WINDOW',
    );
  });

  it('oculta la asignación a un Customer ajeno', async () => {
    const { service } = build();
    const id = createAssignment().id;
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);

    await expectDomainError(
      service.getAccess(id, { uid: 'other', email: 'x@y.z', role: 'CUSTOMER' }),
      'NOT_FOUND',
    );
  });

  it('permite al Admin obtener datos de anfitrión', async () => {
    const { service } = build();
    const id = createAssignment().id;
    await service.createRoom(id, LiveProvider.ZOOM, provider, null);

    const access = await service.getAccess(id, admin);
    expect('hostUrl' in access).toBe(true);
  });
});

describe('SecretCipher', () => {
  it('cifra y descifra secretos sin exponer el texto plano', () => {
    const cipher = createCipher();
    const encrypted = cipher.encrypt('passcode-secreto');
    expect(encrypted).not.toContain('passcode-secreto');
    expect(cipher.decrypt(encrypted)).toBe('passcode-secreto');
  });
});
