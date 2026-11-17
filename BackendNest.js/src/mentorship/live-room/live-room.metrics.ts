import { Injectable, Logger } from '@nestjs/common';
import type { LiveProvider } from './domain/live-room.types.js';

export interface ProviderMetrics {
  attempts: number;
  successes: number;
  failures: number;
  timeouts: number;
  totalLatencyMs: number;
}

function emptyMetrics(): ProviderMetrics {
  return {
    attempts: 0,
    successes: 0,
    failures: 0,
    timeouts: 0,
    totalLatencyMs: 0,
  };
}

/**
 * Métricas por proveedor (NFR-003): intentos, éxitos, fallos, timeouts y
 * latencia acumulada. Solo se etiqueta por proveedor; nunca con datos
 * personales ni secretos.
 */
@Injectable()
export class LiveRoomMetrics {
  private readonly logger = new Logger(LiveRoomMetrics.name);
  private readonly counters = new Map<LiveProvider, ProviderMetrics>();

  recordAttempt(provider: LiveProvider): void {
    this.for(provider).attempts += 1;
  }

  recordSuccess(provider: LiveProvider, latencyMs: number): void {
    const metrics = this.for(provider);
    metrics.successes += 1;
    metrics.totalLatencyMs += latencyMs;
    this.logger.debug(`live_room provider=${provider} outcome=success ms=${latencyMs}`);
  }

  recordFailure(provider: LiveProvider, latencyMs: number): void {
    const metrics = this.for(provider);
    metrics.failures += 1;
    metrics.totalLatencyMs += latencyMs;
    this.logger.warn(`live_room provider=${provider} outcome=failure ms=${latencyMs}`);
  }

  recordTimeout(provider: LiveProvider, latencyMs: number): void {
    const metrics = this.for(provider);
    metrics.timeouts += 1;
    metrics.totalLatencyMs += latencyMs;
    this.logger.warn(`live_room provider=${provider} outcome=timeout ms=${latencyMs}`);
  }

  snapshot(): Record<string, ProviderMetrics> {
    return Object.fromEntries(
      [...this.counters.entries()].map(([provider, metrics]) => [
        provider,
        { ...metrics },
      ]),
    );
  }

  private for(provider: LiveProvider): ProviderMetrics {
    let metrics = this.counters.get(provider);
    if (!metrics) {
      metrics = emptyMetrics();
      this.counters.set(provider, metrics);
    }
    return metrics;
  }
}
