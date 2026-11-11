import { describe, it, expect, vi, afterEach } from 'vitest';
import { Logger } from '@nestjs/common';
import type { ConfigService } from '@nestjs/config';
import { ConsoleMailService } from './console-mail.service.js';

const withEnv = (env: string) =>
  new ConsoleMailService({ get: () => env } as unknown as ConfigService);

describe('ConsoleMailService', () => {
  afterEach(() => vi.restoreAllMocks());

  it('fuera de producción escribe el enlace en el log (para copiarlo en desarrollo)', async () => {
    const log = vi.spyOn(Logger.prototype, 'log').mockImplementation(() => {});

    await withEnv('development').sendPasswordReset(
      'a@example.com',
      'http://x/?token=abc',
    );

    expect(log).toHaveBeenCalledWith(
      expect.stringContaining('http://x/?token=abc'),
    );
  });

  it('en producción NO escribe el enlace: solo avisa de que falta proveedor', async () => {
    const log = vi.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
    const warn = vi
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation(() => {});

    await withEnv('production').sendPasswordReset(
      'a@example.com',
      'http://x/?token=abc',
    );

    expect(log).not.toHaveBeenCalled();
    expect(warn).toHaveBeenCalledWith(expect.not.stringContaining('token=abc'));
  });
});
