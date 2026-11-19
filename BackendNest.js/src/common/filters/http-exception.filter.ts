import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { QueryFailedError } from 'typeorm';

/** Cuerpo uniforme de todo error de la API. */
export interface ErrorResponse {
  statusCode: number;
  error: string;
  message: string | string[];
  path: string;
  timestamp: string;
  /** Regla de negocio incumplida (p. ej. "RN-016"), cuando aplica. */
  rule?: string;
}

/** Códigos de error de PostgreSQL que se traducen a respuestas controladas. */
const PG_UNIQUE_VIOLATION = '23505';
const PG_FOREIGN_KEY_VIOLATION = '23503';

/**
 * Captura TODA excepción (RN-010) y la convierte al formato ErrorResponse.
 * Los errores no controlados se registran con su stack pero se responden
 * como 500 genérico: nunca se filtra información interna al cliente.
 */
@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(HttpExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();
    // originalUrl conserva el prefijo global; req.url llega recortado en routers montados
    const path = request.originalUrl ?? request.url;

    const { statusCode, error, message, rule } = this.describe(exception);

    if (statusCode >= 500) {
      const stack =
        exception instanceof Error ? exception.stack : String(exception);
      this.logger.error(`${request.method} ${path} → ${statusCode}`, stack);
    }

    const body: ErrorResponse = {
      statusCode,
      error,
      message,
      path,
      timestamp: new Date().toISOString(),
      ...(rule ? { rule } : {}),
    };

    response.status(statusCode).json(body);
  }

  private describe(
    exception: unknown,
  ): Omit<ErrorResponse, 'path' | 'timestamp'> {
    if (exception instanceof HttpException) {
      return this.fromHttpException(exception);
    }

    if (exception instanceof QueryFailedError) {
      const code = (exception as QueryFailedError & { code?: string }).code;
      if (code === PG_UNIQUE_VIOLATION) {
        return {
          statusCode: HttpStatus.CONFLICT,
          error: 'Conflict',
          message: 'Ya existe un registro con esos datos',
        };
      }
      if (code === PG_FOREIGN_KEY_VIOLATION) {
        return {
          statusCode: HttpStatus.CONFLICT,
          error: 'Conflict',
          message:
            'El registro está relacionado con otros datos o referencia uno inexistente',
        };
      }
    }

    return {
      statusCode: HttpStatus.INTERNAL_SERVER_ERROR,
      error: 'Internal Server Error',
      message: 'Error interno del servidor',
    };
  }

  private fromHttpException(
    exception: HttpException,
  ): Omit<ErrorResponse, 'path' | 'timestamp'> {
    const statusCode = exception.getStatus();
    const payload = exception.getResponse();
    const fallbackError = HttpStatus[statusCode] ?? 'Error';

    // HttpException('texto') → payload es string
    if (typeof payload === 'string') {
      return { statusCode, error: toTitle(fallbackError), message: payload };
    }

    // new BadRequestException({...}) / ValidationPipe → payload es objeto
    const data = payload as {
      message?: string | string[];
      error?: string;
      rule?: string;
    };
    return {
      statusCode,
      error: data.error ?? toTitle(fallbackError),
      message: data.message ?? exception.message,
      ...(data.rule ? { rule: data.rule } : {}),
    };
  }
}

/** "NOT_FOUND" → "Not Found" */
function toTitle(constant: string): string {
  return constant
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}
