import { DomainErrors } from '../../../common/domain-error.js';
import type { LiveProvider } from '../domain/live-room.types.js';

export interface HttpRequestOptions {
  method: 'GET' | 'POST';
  url: string;
  headers?: Record<string, string>;
  body?: unknown;
  form?: URLSearchParams;
  timeoutMs: number;
  provider: LiveProvider;
}

export interface HttpResult<T> {
  status: number;
  data: T;
}

/**
 * Cliente HTTP mínimo con timeout explícito (FR-014). Un abort por tiempo se
 * mapea a `504 LIVE_PROVIDER_TIMEOUT`; un fallo de red, a `503`.
 */
export async function httpRequest<T>(
  options: HttpRequestOptions,
): Promise<HttpResult<T>> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs);

  try {
    const headers: Record<string, string> = { ...options.headers };
    let body: string | undefined;
    if (options.form) {
      headers['content-type'] = 'application/x-www-form-urlencoded';
      body = options.form.toString();
    } else if (options.body !== undefined) {
      headers['content-type'] = 'application/json';
      body = JSON.stringify(options.body);
    }

    const response = await fetch(options.url, {
      method: options.method,
      headers,
      body,
      signal: controller.signal,
    });

    const text = await response.text();
    const data = text.length > 0 ? (JSON.parse(text) as T) : (undefined as T);
    return { status: response.status, data };
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw DomainErrors.providerTimeout(options.provider);
    }
    throw DomainErrors.providerUnavailable(
      options.provider,
      'no se pudo contactar al proveedor',
    );
  } finally {
    clearTimeout(timer);
  }
}

/** Falla controlada para respuestas `5xx`/`429`/`401` del proveedor. */
export function assertProviderOk(
  status: number,
  provider: LiveProvider,
  detail: string,
): void {
  if (status >= 200 && status < 300) {
    return;
  }
  if (status === 401 || status === 403) {
    throw DomainErrors.providerUnavailable(provider, 'autenticación rechazada');
  }
  if (status === 429) {
    throw DomainErrors.providerUnavailable(provider, 'límite de tasa excedido');
  }
  if (status >= 500) {
    throw DomainErrors.providerUnavailable(provider, 'error del proveedor');
  }
  throw DomainErrors.providerUnavailable(provider, detail);
}
