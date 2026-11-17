import { Injectable, type NestMiddleware } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';

export const CORRELATION_HEADER = 'x-correlation-id';

export interface CorrelatedRequest extends Request {
  correlationId?: string;
}

/**
 * Propaga `X-Correlation-Id` (FR-018): reutiliza el valor entrante o genera uno
 * nuevo, lo expone en la petición y lo devuelve siempre en la respuesta.
 */
@Injectable()
export class CorrelationMiddleware implements NestMiddleware {
  use(req: Request, res: Response, next: NextFunction): void {
    const incoming = req.headers[CORRELATION_HEADER];
    const headerValue = Array.isArray(incoming) ? incoming[0] : incoming;
    const correlationId =
      headerValue && headerValue.trim().length > 0 ? headerValue : randomUUID();

    (req as CorrelatedRequest).correlationId = correlationId;
    res.setHeader('X-Correlation-Id', correlationId);
    next();
  }
}
