import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, catchError, firstValueFrom, throwError } from 'rxjs';
import { apiBase } from './api';

export interface KnowledgeSource {
  sourceId: string;
  title: string;
  snippet: string;
  score: number;
}

export interface KnowledgeAnswer {
  answer: string;
  hasSufficientContext: boolean;
  sources: KnowledgeSource[];
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [key: string]: unknown;
}

export class KnowledgeApiError extends Error {
  readonly status: number;
  readonly detail?: string;
  readonly title?: string;
  readonly problemDetail?: ProblemDetail;

  constructor(
    status: number,
    message: string,
    detail?: string,
    title?: string,
    problemDetail?: ProblemDetail
  ) {
    super(message);
    this.name = 'KnowledgeApiError';
    this.status = status;
    this.detail = detail;
    this.title = title;
    this.problemDetail = problemDetail;

    Object.setPrototypeOf(this, KnowledgeApiError.prototype);
  }

  static fromHttpError(err: unknown): KnowledgeApiError {
    if (err instanceof KnowledgeApiError) {
      return err;
    }
    if (err instanceof HttpErrorResponse) {
      const status = err.status || 0;
      const problem = (typeof err.error === 'object' && err.error !== null)
        ? (err.error as ProblemDetail)
        : undefined;
      const detail = problem?.detail || (typeof err.error === 'string' ? err.error : undefined);
      const title = problem?.title || err.statusText;
      const message = detail || title || err.message || `HTTP ${status}`;
      return new KnowledgeApiError(status, message, detail, title, problem);
    }
    if (typeof err === 'object' && err !== null && 'status' in err) {
      const anyErr = err as { status?: number; message?: string; error?: unknown };
      const status = typeof anyErr.status === 'number' ? anyErr.status : 0;
      const problem = (typeof anyErr.error === 'object' && anyErr.error !== null)
        ? (anyErr.error as ProblemDetail)
        : undefined;
      const detail = problem?.detail || (typeof anyErr.error === 'string' ? anyErr.error : undefined);
      const message = detail || anyErr.message || `HTTP ${status}`;
      return new KnowledgeApiError(status, message, detail, undefined, problem);
    }
    const message = err instanceof Error ? err.message : String(err);
    return new KnowledgeApiError(0, message);
  }
}

export function getXsrfToken(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  if (!match) {
    return null;
  }
  const token = decodeURIComponent(match[1]);
  return token.replace(/^"(.*)"$/, '$1');
}

export type KnowledgeResponse = Observable<KnowledgeAnswer> & Promise<KnowledgeAnswer>;

function makePromiseCompatible(obs$: Observable<KnowledgeAnswer>): KnowledgeResponse {
  const target = obs$ as unknown as KnowledgeResponse;

  target.then = function <TResult1 = KnowledgeAnswer, TResult2 = never>(
    onfulfilled?: ((value: KnowledgeAnswer) => TResult1 | PromiseLike<TResult1>) | null,
    onrejected?: ((reason: any) => TResult2 | PromiseLike<TResult2>) | null
  ): Promise<TResult1 | TResult2> {
    return firstValueFrom(obs$).then(onfulfilled, onrejected);
  };

  target.catch = function <TResult = never>(
    onrejected?: ((reason: any) => TResult | PromiseLike<TResult>) | null
  ): Promise<KnowledgeAnswer | TResult> {
    return firstValueFrom(obs$).catch(onrejected);
  };

  target.finally = function (
    onfinally?: (() => void) | null
  ): Promise<KnowledgeAnswer> {
    return firstValueFrom(obs$).finally(onfinally);
  };

  return target;
}

export function askKnowledge(
  question: string,
  http?: HttpClient
): KnowledgeResponse {
  const base = apiBase().replace(/\/$/, '');
  const url = `${base}/ai/knowledge/ask`;
  const xsrfToken = getXsrfToken();

  let client = http;
  if (!client) {
    try {
      client = inject(HttpClient);
    } catch {
      // Not in an Angular injection context
    }
  }

  let obs$: Observable<KnowledgeAnswer>;

  if (client) {
    let headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });
    if (xsrfToken) {
      headers = headers.set('X-XSRF-TOKEN', xsrfToken);
    }

    obs$ = client
      .post<KnowledgeAnswer>(
        url,
        { question },
        {
          headers,
          withCredentials: true
        }
      )
      .pipe(
        catchError((err: unknown) => {
          return throwError(() => KnowledgeApiError.fromHttpError(err));
        })
      );
  } else {
    obs$ = new Observable<KnowledgeAnswer>((subscriber) => {
      const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
      const headers: Record<string, string> = {
        'Content-Type': 'application/json'
      };
      if (xsrfToken) {
        headers['X-XSRF-TOKEN'] = xsrfToken;
      }

      fetch(url, {
        method: 'POST',
        headers,
        credentials: 'include',
        body: JSON.stringify({ question }),
        signal: controller?.signal
      })
        .then(async (response) => {
          if (!response.ok) {
            let problem: ProblemDetail | undefined;
            let bodyText = '';
            try {
              problem = await response.json();
            } catch {
              try {
                bodyText = await response.text();
              } catch {
                // ignore
              }
            }
            const status = response.status;
            const detail = problem?.detail || bodyText || undefined;
            const title = problem?.title || response.statusText;
            const message = detail || title || `HTTP ${status}`;
            subscriber.error(new KnowledgeApiError(status, message, detail, title, problem));
            return;
          }
          const data: KnowledgeAnswer = await response.json();
          subscriber.next(data);
          subscriber.complete();
        })
        .catch((err) => {
          if (err?.name === 'AbortError') return;
          subscriber.error(KnowledgeApiError.fromHttpError(err));
        });

      return () => {
        controller?.abort();
      };
    });
  }

  return makePromiseCompatible(obs$);
}

@Injectable({
  providedIn: 'root'
})
export class KnowledgeApiService {
  constructor(private http: HttpClient) {}

  askKnowledge(question: string): KnowledgeResponse {
    return askKnowledge(question, this.http);
  }
}
