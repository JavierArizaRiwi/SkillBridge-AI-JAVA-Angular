import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getDataSourceToken } from '@nestjs/typeorm';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { AppModule } from '../src/app.module.js';
import { LiveProvider } from '../src/mentorship/live-room/domain/live-room.types.js';
import { LIVE_MEETING_ADAPTERS } from '../src/mentorship/live-room/ports/live-meeting.port.js';
import { LIVE_ROOM_REPOSITORY } from '../src/mentorship/live-room/ports/live-room.repository.js';
import { createFakeDataSource } from './helpers/fake-data-source.js';
import {
  FakeLiveMeetingAdapter,
  FakeLiveRoomRepository,
  createAssignment,
} from './helpers/live-room-fixtures.js';
import { getTestKeys } from './helpers/test-keys.js';

const ASSIGNMENT = createAssignment();

function token(role: 'CUSTOMER' | 'PROVIDER' | 'ADMIN', uid: string): string {
  return jwt.sign(
    { uid, email: `${uid}@example.com`, role, iss: 'skillbridge' },
    getTestKeys().privateKey,
    { algorithm: 'RS256', expiresIn: '1h' },
  );
}

describe('LiveRoom HTTP API', () => {
  let app: INestApplication;
  let repository: FakeLiveRoomRepository;
  let zoom: FakeLiveMeetingAdapter;

  beforeEach(async () => {
    repository = new FakeLiveRoomRepository();
    repository.addAssignment(ASSIGNMENT);
    zoom = new FakeLiveMeetingAdapter(LiveProvider.ZOOM, {
      externalRoomId: '999',
      joinUrl: 'https://zoom.example/j/999',
      hostUrl: 'https://zoom.example/s/999',
      passcode: 'secret-pass',
      hostSignature: 'host-signature',
    });

    const moduleRef = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(getDataSourceToken())
      .useValue(createFakeDataSource())
      .overrideProvider(LIVE_ROOM_REPOSITORY)
      .useValue(repository)
      .overrideProvider(LIVE_MEETING_ADAPTERS)
      .useValue(new Map([[LiveProvider.ZOOM, zoom]]))
      .compile();

    app = moduleRef.createNestApplication();
    await app.init();
  });

  afterEach(async () => {
    await app.close();
  });

  const url = `/api/v1/mentorships/${ASSIGNMENT.id}/live-room`;

  it('rechaza sin token con 401 y cuerpo de contrato', async () => {
    const response = await request(app.getHttpServer())
      .post(url)
      .send({ provider: 'ZOOM' })
      .expect(401);

    expect(response.body.code).toBe('UNAUTHENTICATED');
    expect(response.body.correlationId).toBeTruthy();
  });

  it('rechaza a un CUSTOMER con 403', async () => {
    const response = await request(app.getHttpServer())
      .post(url)
      .set('Authorization', `Bearer ${token('CUSTOMER', 'customer-1')}`)
      .send({ provider: 'ZOOM' })
      .expect(403);

    expect(response.body.code).toBe('FORBIDDEN');
  });

  it('crea la sala (201) y es idempotente (200)', async () => {
    const auth = `Bearer ${token('PROVIDER', 'provider-1')}`;

    const created = await request(app.getHttpServer())
      .post(url)
      .set('Authorization', auth)
      .set('X-Correlation-Id', 'corr-http')
      .send({ provider: 'ZOOM' })
      .expect(201);

    expect(created.body.hostUrl).toBe('https://zoom.example/s/999');
    expect(created.headers['x-correlation-id']).toBe('corr-http');
    expect(repository.outbox).toHaveLength(1);

    const repeated = await request(app.getHttpServer())
      .post(url)
      .set('Authorization', auth)
      .send({ provider: 'ZOOM' })
      .expect(200);

    expect(repeated.body.assignmentId).toBe(ASSIGNMENT.id);
    expect(zoom.calls).toBe(1);
  });

  it('devuelve 400 VALIDATION_ERROR con provider inválido', async () => {
    const response = await request(app.getHttpServer())
      .post(url)
      .set('Authorization', `Bearer ${token('PROVIDER', 'provider-1')}`)
      .send({ provider: 'INVALID' })
      .expect(400);

    expect(response.body.code).toBe('VALIDATION_ERROR');
  });

  it('devuelve 400 UNSUPPORTED_LIVE_PROVIDER para CUSTOM_URL', async () => {
    const response = await request(app.getHttpServer())
      .post(url)
      .set('Authorization', `Bearer ${token('PROVIDER', 'provider-1')}`)
      .send({ provider: 'CUSTOM_URL' })
      .expect(400);

    expect(response.body.code).toBe('UNSUPPORTED_LIVE_PROVIDER');
  });

  it('oculta la asignación de otro Provider con 404', async () => {
    const response = await request(app.getHttpServer())
      .post(url)
      .set('Authorization', `Bearer ${token('PROVIDER', 'other-provider')}`)
      .send({ provider: 'ZOOM' })
      .expect(404);

    expect(response.body.code).toBe('NOT_FOUND');
  });

  it('entrega acceso de participante al Customer sin secretos de anfitrión', async () => {
    await request(app.getHttpServer())
      .post(url)
      .set('Authorization', `Bearer ${token('PROVIDER', 'provider-1')}`)
      .send({ provider: 'ZOOM' })
      .expect(201);

    const access = await request(app.getHttpServer())
      .get(`/api/v1/mentorships/${ASSIGNMENT.id}/access`)
      .set('Authorization', `Bearer ${token('CUSTOMER', 'customer-1')}`)
      .expect(200);

    expect(access.body.joinUrl).toBe('https://zoom.example/j/999');
    expect(access.body.passcode).toBe('secret-pass');
    expect(access.body.hostUrl).toBeUndefined();
    expect(access.body.hostSignature).toBeUndefined();
  });

  it('exige X-XSRF-TOKEN cuando autentica por cookie', async () => {
    const cookie = `access_token=${token('PROVIDER', 'provider-1')}`;

    await request(app.getHttpServer())
      .post(url)
      .set('Cookie', cookie)
      .send({ provider: 'ZOOM' })
      .expect(403);

    await request(app.getHttpServer())
      .post(url)
      .set('Cookie', cookie)
      .set('X-XSRF-TOKEN', 'csrf-token')
      .send({ provider: 'ZOOM' })
      .expect(201);
  });
});
