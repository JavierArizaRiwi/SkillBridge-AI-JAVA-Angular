import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { apiBase } from './api';
import {
  KnowledgeAnswer,
  KnowledgeApiError,
  KnowledgeApiService,
  KnowledgeSource,
  ProblemDetail,
  askKnowledge,
  getXsrfToken
} from './knowledge.api';

describe('Knowledge API Client (HU-26, T031)', () => {
  let httpMock: HttpTestingController;
  let http: HttpClient;
  let service: KnowledgeApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [KnowledgeApiService]
    });

    httpMock = TestBed.inject(HttpTestingController);
    http = TestBed.inject(HttpClient);
    service = TestBed.inject(KnowledgeApiService);

    // Limpia cookie XSRF antes de cada prueba
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  afterEach(() => {
    httpMock.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  describe('getXsrfToken', () => {
    it('debe retornar null cuando no existe la cookie XSRF-TOKEN', () => {
      document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
      expect(getXsrfToken()).toBeNull();
    });

    it('debe leer y decodificar la cookie XSRF-TOKEN', () => {
      document.cookie = 'XSRF-TOKEN=secret%20token%20123; path=/';
      expect(getXsrfToken()).toBe('secret token 123');
    });

    it('debe quitar comillas si la cookie viene entrecomillada', () => {
      document.cookie = 'XSRF-TOKEN="quoted-token"; path=/';
      expect(getXsrfToken()).toBe('quoted-token');
    });
  });

  describe('askKnowledge con HttpClient (Observable)', () => {
    const mockAnswer: KnowledgeAnswer = {
      answer: 'Puedes cancelar hasta 24 horas antes sin penalización.',
      hasSufficientContext: true,
      sources: [
        {
          sourceId: 'cancellation-policy',
          title: 'Política de cancelación',
          snippet: 'Las cancelaciones deben realizarse al menos 24 horas antes...',
          score: 0.92
        }
      ]
    };

    it('debe enviar POST a /api/ai/knowledge/ask con el cuerpo correcto y withCredentials', (done) => {
      document.cookie = 'XSRF-TOKEN=xsrf-test-value; path=/';

      askKnowledge('¿Con cuánta anticipación puedo cancelar?', http).subscribe({
        next: (response) => {
          expect(response).toEqual(mockAnswer);
          expect(response.hasSufficientContext).toBeTrue();
          expect(response.sources.length).toBe(1);
          expect(response.sources[0].sourceId).toBe('cancellation-policy');
          done();
        },
        error: (err) => done.fail(err)
      });

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ question: '¿Con cuánta anticipación puedo cancelar?' });
      expect(req.request.withCredentials).toBeTrue();
      expect(req.request.headers.get('X-XSRF-TOKEN')).toBe('xsrf-test-value');

      req.flush(mockAnswer);
    });

    it('debe manejar caso sin información (hasSufficientContext: false)', (done) => {
      const emptyAnswer: KnowledgeAnswer = {
        answer: 'No dispongo de información suficiente sobre esa consulta en la plataforma.',
        hasSufficientContext: false,
        sources: []
      };

      service.askKnowledge('¿Cómo reparar un motor diésel?').subscribe({
        next: (response) => {
          expect(response.hasSufficientContext).toBeFalse();
          expect(response.sources).toEqual([]);
          expect(response.answer).toContain('No dispongo de información suficiente');
          done();
        },
        error: (err) => done.fail(err)
      });

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      expect(req.request.method).toBe('POST');
      req.flush(emptyAnswer);
    });

    it('debe propagar error 503 como KnowledgeApiError tipado con ProblemDetail', (done) => {
      const problemDetail: ProblemDetail = {
        type: 'https://skillbridge.com/problems/ai-unavailable',
        title: 'Servicio de IA no disponible',
        status: 503,
        detail: 'El proveedor de modelos de lenguaje superó el tiempo de espera.'
      };

      askKnowledge('pregunta con fallo', http).subscribe({
        next: () => done.fail('Se esperaba error 503'),
        error: (err: KnowledgeApiError) => {
          expect(err instanceof KnowledgeApiError).toBeTrue();
          expect(err.status).toBe(503);
          expect(err.detail).toBe('El proveedor de modelos de lenguaje superó el tiempo de espera.');
          expect(err.title).toBe('Servicio de IA no disponible');
          expect(err.problemDetail?.status).toBe(503);
          done();
        }
      });

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush(problemDetail, { status: 503, statusText: 'Service Unavailable' });
    });

    it('debe propagar error 429 como KnowledgeApiError tipado (rate limit)', (done) => {
      const problemDetail: ProblemDetail = {
        title: 'Límite de cuota excedido',
        status: 429,
        detail: 'Has superado el límite de consultas permitidas por minuto.'
      };

      askKnowledge('pregunta rate limited', http).subscribe({
        next: () => done.fail('Se esperaba error 429'),
        error: (err: KnowledgeApiError) => {
          expect(err instanceof KnowledgeApiError).toBeTrue();
          expect(err.status).toBe(429);
          expect(err.detail).toContain('límite de consultas');
          done();
        }
      });

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush(problemDetail, { status: 429, statusText: 'Too Many Requests' });
    });

    it('debe propagar error 400 como KnowledgeApiError tipado (validación de pregunta)', (done) => {
      const problemDetail: ProblemDetail = {
        title: 'Solicitud inválida',
        status: 400,
        detail: 'La pregunta no puede estar vacía ni superar 500 caracteres.'
      };

      askKnowledge('', http).subscribe({
        next: () => done.fail('Se esperaba error 400'),
        error: (err: KnowledgeApiError) => {
          expect(err instanceof KnowledgeApiError).toBeTrue();
          expect(err.status).toBe(400);
          expect(err.detail).toContain('no puede estar vacía');
          done();
        }
      });

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush(problemDetail, { status: 400, statusText: 'Bad Request' });
    });

    it('debe propagar error 401 como KnowledgeApiError tipado (sin autenticación)', (done) => {
      askKnowledge('¿pregunta sin auth?', http).subscribe({
        next: () => done.fail('Se esperaba error 401'),
        error: (err: KnowledgeApiError) => {
          expect(err instanceof KnowledgeApiError).toBeTrue();
          expect(err.status).toBe(401);
          done();
        }
      });

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush({ detail: 'No autenticado' }, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('askKnowledge compatibilidad con Promise / async-await', () => {
    it('debe resolverse con await retornando KnowledgeAnswer', async () => {
      const promise = askKnowledge('¿Horarios disponibles?', http).then(res => res);

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush({
        answer: 'Los horarios dependen de cada mentor.',
        hasSufficientContext: true,
        sources: []
      });

      const res = await promise;
      expect(res.answer).toBe('Los horarios dependen de cada mentor.');
      expect(res.hasSufficientContext).toBeTrue();
    });

    it('debe rechazarse con await ante un error 503', async () => {
      const promise = askKnowledge('¿Fallo 503?', http).then(res => res);

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush({ detail: 'Servicio no disponible' }, { status: 503, statusText: 'Service Unavailable' });

      try {
        await promise;
        fail('Se esperaba que la promesa fuera rechazada');
      } catch (err) {
        const apiError = err as KnowledgeApiError;
        expect(apiError instanceof KnowledgeApiError).toBeTrue();
        expect(apiError.status).toBe(503);
      }
    });

    it('debe resolverse directamente usando firstValueFrom', async () => {
      const { firstValueFrom } = await import('rxjs');
      const promise = firstValueFrom(askKnowledge('¿Pregunta con firstValueFrom?', http));

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush({
        answer: 'Respuesta vía firstValueFrom',
        hasSufficientContext: true,
        sources: []
      });

      const res = await promise;
      expect(res.answer).toBe('Respuesta vía firstValueFrom');
    });
  });

  describe('askKnowledge fallback con fetch (sin HttpClient en DI)', () => {
    let originalFetch: typeof window.fetch;

    beforeEach(() => {
      originalFetch = window.fetch;
    });

    afterEach(() => {
      window.fetch = originalFetch;
    });

    it('debe usar fetch cuando no hay HttpClient disponible', (done) => {
      const expectedAnswer: KnowledgeAnswer = {
        answer: 'Respuesta vía fetch fallback',
        hasSufficientContext: true,
        sources: []
      };

      let fetchCalled = false;
      window.fetch = jasmine.createSpy('fetch').and.callFake((url: RequestInfo | URL, init?: RequestInit) => {
        fetchCalled = true;
        expect(url.toString()).toContain('/ai/knowledge/ask');
        expect(init?.method).toBe('POST');
        expect(init?.credentials).toBe('include');
        expect(JSON.parse(init?.body as string)).toEqual({ question: 'test fetch' });

        return Promise.resolve(new Response(JSON.stringify(expectedAnswer), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        }));
      });

      askKnowledge('test fetch').subscribe({
        next: (ans) => {
          expect(fetchCalled).toBeTrue();
          expect(ans.answer).toBe('Respuesta vía fetch fallback');
          done();
        },
        error: (e) => done.fail(e)
      });
    });

    it('debe propagar error 503 tipado cuando fetch retorna 503', (done) => {
      window.fetch = jasmine.createSpy('fetch').and.callFake(() => {
        return Promise.resolve(new Response(JSON.stringify({ detail: 'LLM timeout', status: 503 }), {
          status: 503,
          statusText: 'Service Unavailable',
          headers: { 'Content-Type': 'application/json' }
        }));
      });

      askKnowledge('test fetch 503').subscribe({
        next: () => done.fail('Se esperaba error'),
        error: (err: KnowledgeApiError) => {
          expect(err instanceof KnowledgeApiError).toBeTrue();
          expect(err.status).toBe(503);
          expect(err.detail).toBe('LLM timeout');
          done();
        }
      });
    });
  });

  describe('Invariante de almacenamiento', () => {
    it('no debe acceder a localStorage ni sessionStorage', () => {
      spyOn(localStorage, 'getItem').and.callThrough();
      spyOn(localStorage, 'setItem').and.callThrough();
      spyOn(sessionStorage, 'getItem').and.callThrough();
      spyOn(sessionStorage, 'setItem').and.callThrough();

      const sub = askKnowledge('¿Pregunta inocua?', http).subscribe();

      const req = httpMock.expectOne(`${apiBase()}/ai/knowledge/ask`);
      req.flush({ answer: 'ok', hasSufficientContext: true, sources: [] });
      sub.unsubscribe();

      expect(localStorage.getItem).not.toHaveBeenCalled();
      expect(localStorage.setItem).not.toHaveBeenCalled();
      expect(sessionStorage.getItem).not.toHaveBeenCalled();
      expect(sessionStorage.setItem).not.toHaveBeenCalled();
    });
  });
});
