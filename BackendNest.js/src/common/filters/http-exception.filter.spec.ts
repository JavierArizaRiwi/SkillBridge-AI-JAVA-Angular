import {
  ArgumentsHost,
  BadRequestException,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { QueryFailedError } from 'typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../exceptions/index.js';
import { HttpExceptionFilter } from './http-exception.filter.js';

function mockHost(url = '/api/v1/tables/9', method = 'GET') {
  const json = vi.fn();
  const status = vi.fn().mockReturnValue({ json });
  const host = {
    switchToHttp: () => ({
      getResponse: () => ({ status }),
      getRequest: () => ({
        url: url.replace(/^\/api/, ''),
        originalUrl: url,
        method,
      }),
    }),
  } as unknown as ArgumentsHost;
  return { host, status, json };
}

describe('HttpExceptionFilter', () => {
  let filter: HttpExceptionFilter;
  let loggerError: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    filter = new HttpExceptionFilter();
    loggerError = vi
      .spyOn(Logger.prototype, 'error')
      .mockImplementation(() => undefined);
  });

  afterEach(() => vi.restoreAllMocks());

  it('formatea una HttpException con mensaje de texto', () => {
    const { host, status, json } = mockHost();

    filter.catch(new HttpException('Prohibido', HttpStatus.FORBIDDEN), host);

    expect(status).toHaveBeenCalledWith(403);
    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({
        statusCode: 403,
        error: 'Forbidden',
        message: 'Prohibido',
        path: '/api/v1/tables/9',
        timestamp: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T/),
      }),
    );
    expect(loggerError).not.toHaveBeenCalled();
  });

  it('conserva el array de mensajes que produce el ValidationPipe', () => {
    const { host, json } = mockHost('/api/v1/tables', 'POST');

    filter.catch(
      new BadRequestException([
        'number must be positive',
        'capacity must be > 0',
      ]),
      host,
    );

    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({
        statusCode: 400,
        error: 'Bad Request',
        message: ['number must be positive', 'capacity must be > 0'],
      }),
    );
  });

  it('EntityNotFoundException → 404 con mensaje de dominio', () => {
    const { host, json } = mockHost();

    filter.catch(new EntityNotFoundException('Mesa', 9), host);

    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({
        statusCode: 404,
        error: 'Not Found',
        message: 'Mesa con id 9 no existe',
      }),
    );
  });

  it('BusinessRuleException → 409 e incluye la regla incumplida', () => {
    const { host, json } = mockHost('/api/v1/tables', 'POST');

    filter.catch(
      new BusinessRuleException('El número de mesa ya existe', 'RN-016'),
      host,
    );

    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({
        statusCode: 409,
        error: 'Conflict',
        message: 'El número de mesa ya existe',
        rule: 'RN-016',
      }),
    );
  });

  it('violación de índice único de PostgreSQL → 409', () => {
    const { host, json } = mockHost('/api/v1/tables', 'POST');
    const dbError = new QueryFailedError(
      'INSERT ...',
      [],
      new Error('duplicate key'),
    );
    (dbError as QueryFailedError & { code?: string }).code = '23505';

    filter.catch(dbError, host);

    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({ statusCode: 409, error: 'Conflict' }),
    );
  });

  it('error inesperado → 500 genérico, sin detalles, y se registra con stack', () => {
    const { host, json } = mockHost();
    const boom = new Error('secreto interno: password=123');

    filter.catch(boom, host);

    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({
        statusCode: 500,
        error: 'Internal Server Error',
        message: 'Error interno del servidor',
      }),
    );
    const body = json.mock.calls[0][0] as Record<string, unknown>;
    expect(JSON.stringify(body)).not.toContain('secreto interno');
    expect(loggerError).toHaveBeenCalledWith(
      expect.stringContaining('GET /api/v1/tables/9 → 500'),
      boom.stack,
    );
  });
});
