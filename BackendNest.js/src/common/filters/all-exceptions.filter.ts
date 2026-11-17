import {
  ArgumentsHost,
  Catch,
  type ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import type { Response } from 'express';
import { randomUUID } from 'node:crypto';
import { QueryFailedError } from 'typeorm';
import { DomainError, type ErrorCode } from '../domain-error.js';
import type { CorrelatedRequest } from '../correlation.middleware.js';

/** Cuerpo uniforme de error del contrato de mentorías (NFR-004). */
export interface MentorshipErrorBody {
  code: ErrorCode;
  message: string;
  correlationId: string;
  rule?: string;
}

const PG_UNIQUE_VIOLATION = '23505';

/**
 * Convierte toda excepción al cuerpo `{ code, message, correlationId }`.
 * Nunca filtra información interna en errores no controlados.
 */
@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  private readonly logger = new Logger(AllExceptionsFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<CorrelatedRequest>();
    const correlationId =
      request.correlationId ??
      (request.headers?.['x-correlation-id'] as string | undefined) ??
      randomUUID();

    const { status, body } = this.describe(exception, correlationId);

    if (status >= 500) {
      const stack =
        exception instanceof Error ? exception.stack : String(exception);
      this.logger.error(
        `${request.method} ${request.url} → ${status} correlationId=${correlationId}`,
        stack,
      );
    }

    response.status(status).json(body);
  }

  private describe(
    exception: unknown,
    correlationId: string,
  ): { status: number; body: MentorshipErrorBody } {
    if (exception instanceof DomainError) {
      return {
        status: exception.httpStatus,
        body: {
          code: exception.code,
          message: exception.message,
          correlationId,
          ...(exception.rule ? { rule: exception.rule } : {}),
        },
      };
    }

    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      return { status, body: this.fromHttpException(exception, correlationId) };
    }

    if (exception instanceof QueryFailedError) {
      const code = (exception as QueryFailedError & { code?: string }).code;
      if (code === PG_UNIQUE_VIOLATION) {
        return {
          status: HttpStatus.CONFLICT,
          body: {
            code: 'LIVE_ROOM_ALREADY_EXISTS',
            message: 'Ya existe un registro para esa asignación',
            correlationId,
          },
        };
      }
    }

    return {
      status: HttpStatus.INTERNAL_SERVER_ERROR,
      body: {
        code: 'INTERNAL_ERROR',
        message: 'Error interno del servidor',
        correlationId,
      },
    };
  }

  private fromHttpException(
    exception: HttpException,
    correlationId: string,
  ): MentorshipErrorBody {
    const status = exception.getStatus();
    const payload = exception.getResponse();
    const message = this.extractMessage(payload, exception.message);

    const codeByStatus: Record<number, ErrorCode> = {
      [HttpStatus.BAD_REQUEST]: 'VALIDATION_ERROR',
      [HttpStatus.UNAUTHORIZED]: 'UNAUTHENTICATED',
      [HttpStatus.FORBIDDEN]: 'FORBIDDEN',
      [HttpStatus.NOT_FOUND]: 'NOT_FOUND',
      [HttpStatus.CONFLICT]: 'INVALID_STATE_TRANSITION',
    };

    return {
      code: codeByStatus[status] ?? 'INTERNAL_ERROR',
      message,
      correlationId,
    };
  }

  private extractMessage(payload: string | object, fallback: string): string {
    if (typeof payload === 'string') {
      return payload;
    }
    const data = payload as { message?: string | string[] };
    if (Array.isArray(data.message)) {
      return data.message.join(', ');
    }
    return data.message ?? fallback;
  }
}
