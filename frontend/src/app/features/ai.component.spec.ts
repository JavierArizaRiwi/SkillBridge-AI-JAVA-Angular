import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AiComponent } from './ai.component';
import { KnowledgeAnswer } from '../core/knowledge.api';
import { apiBase } from '../core/api';

describe('AiComponent — Chat IA Knowledge (HU-26, User Story 3)', () => {
  let component: AiComponent;
  let fixture: ComponentFixture<AiComponent>;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  const askEndpoint = `${apiBase().replace(/\/$/, '')}/ai/knowledge/ask`;

  beforeEach(async () => {
    localStorage.clear();
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [AiComponent, HttpClientTestingModule],
      providers: [
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('debe crearse con estado inicial reactivo usando Signals', () => {
    expect(component).toBeTruthy();
    expect(component.mode()).toBe('recommendation');
    expect(component.knowledgeQuestion()).toBe('');
    expect(component.knowledgeAnswer()).toBeNull();
    expect(component.knowledgeLoading()).toBeFalse();
    expect(component.knowledgeError()).toBeNull();
    expect(component.hasSufficientContext()).toBeFalse();
    expect(component.sources()).toEqual([]);
    expect(component.formattedSources()).toEqual([]);
  });

  it('debe permitir cambiar al modo de preguntas de conocimiento y sincronizar la entrada', () => {
    component.setMode('knowledge');
    fixture.detectChanges();

    expect(component.mode()).toBe('knowledge');

    component.onTextInput('¿Cómo funciona la cancelación?');
    expect(component.knowledgeQuestion()).toBe('¿Cómo funciona la cancelación?');
    expect(component.question).toBe('¿Cómo funciona la cancelación?');
    expect(component.goal).toBe('¿Cómo funciona la cancelación?');
  });

  describe('Escenario 1: Respuesta exitosa con fuentes citadas', () => {
    const mockAnswerWithSources: KnowledgeAnswer = {
      answer: 'Puedes cancelar tu reserva sin penalización con al menos 24 horas de anticipación.',
      hasSufficientContext: true,
      sources: [
        {
          sourceId: 'cancellation-policy',
          title: 'Política de cancelación',
          snippet: 'Las cancelaciones realizadas con más de 24 horas de antelación reciben un reembolso del 100%.',
          score: 0.82
        },
        {
          sourceId: 'terms-and-conditions',
          title: 'Términos del Servicio',
          snippet: 'El usuario puede reprogramar o cancelar sesiones según las políticas vigentes de la plataforma.',
          score: 0.745
        }
      ]
    };

    it('muestra el texto de la respuesta y tarjetas de fuentes con título, extracto y porcentaje de relevancia', fakeAsync(() => {
      component.setMode('knowledge');
      fixture.detectChanges();

      const userQuestion = '¿Con cuánta anticipación puedo cancelar mi reserva?';
      component.askKnowledgeQuestion(userQuestion);
      expect(component.knowledgeLoading()).toBeTrue();
      fixture.detectChanges();

      const req = httpMock.expectOne(askEndpoint);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ question: userQuestion });
      req.flush(mockAnswerWithSources);
      tick();
      fixture.detectChanges();

      // Signals reactivos
      expect(component.knowledgeLoading()).toBeFalse();
      expect(component.knowledgeAnswer()).toEqual(mockAnswerWithSources);
      expect(component.hasSufficientContext()).toBeTrue();
      expect(component.sources().length).toBe(2);
      expect(component.formattedSources()[0].percentage).toBe('82 %');
      expect(component.formattedSources()[1].percentage).toBe('75 %');

      // DOM: Texto de la respuesta
      const compiled = fixture.nativeElement as HTMLElement;
      const answerElement = compiled.querySelector('[data-testid="knowledge-answer-text"]');
      expect(answerElement).toBeTruthy();
      expect(answerElement?.textContent).toContain('Puedes cancelar tu reserva sin penalización');

      // DOM: Sección y tarjetas de fuentes
      const sourcesSection = compiled.querySelector('[data-testid="sources-section"]');
      expect(sourcesSection).toBeTruthy();

      const sourceCards = compiled.querySelectorAll('[data-testid="source-card"]');
      expect(sourceCards.length).toBe(2);

      // Primera tarjeta
      const firstCard = sourceCards[0];
      expect(firstCard.querySelector('[data-testid="source-title"]')?.textContent).toContain('Política de cancelación');
      expect(firstCard.querySelector('[data-testid="source-snippet"]')?.textContent).toContain('reembolso del 100%');
      expect(firstCard.querySelector('[data-testid="source-score-badge"]')?.textContent).toContain('82 %');
      expect(firstCard.querySelector('[data-testid="source-relevance"]')?.textContent).toContain('82 %');

      // Segunda tarjeta
      const secondCard = sourceCards[1];
      expect(secondCard.querySelector('[data-testid="source-title"]')?.textContent).toContain('Términos del Servicio');
      expect(secondCard.querySelector('[data-testid="source-snippet"]')?.textContent).toContain('reprogramar o cancelar');
      expect(secondCard.querySelector('[data-testid="source-score-badge"]')?.textContent).toContain('75 %');
      expect(secondCard.querySelector('[data-testid="source-relevance"]')?.textContent).toContain('75 %');

      // Garantía: no se guarda en localStorage
      expect(localStorage.getItem('knowledgeAnswer')).toBeNull();
      expect(localStorage.getItem('knowledge')).toBeNull();
      expect(localStorage.length).toBe(0);
    }));

    it('debe responder al método alias askKnowledge igual que askKnowledgeQuestion', fakeAsync(() => {
      component.askKnowledge('¿Qué mentorías ofrecen sobre Spring Boot?');
      const req = httpMock.expectOne(askEndpoint);
      req.flush(mockAnswerWithSources);
      tick();
      fixture.detectChanges();

      expect(component.mode()).toBe('knowledge');
      expect(component.knowledgeAnswer()?.answer).toContain('Puedes cancelar');
      expect(component.sources().length).toBe(2);
    }));
  });

  describe('Escenario 2: Sin contexto suficiente (hasSufficientContext: false)', () => {
    const mockAnswerInsufficient: KnowledgeAnswer = {
      answer: 'No dispongo de información suficiente sobre esa consulta en la plataforma.',
      hasSufficientContext: false,
      sources: []
    };

    it('muestra aviso neutro («No dispongo de información suficiente…») y NO muestra la sección de fuentes', fakeAsync(() => {
      component.setMode('knowledge');
      fixture.detectChanges();

      const offTopicQuery = '¿Cómo reparar la transmisión de un vehículo diésel?';
      component.askKnowledgeQuestion(offTopicQuery);
      fixture.detectChanges();

      const req = httpMock.expectOne(askEndpoint);
      req.flush(mockAnswerInsufficient);
      tick();
      fixture.detectChanges();

      // Signals
      expect(component.hasSufficientContext()).toBeFalse();
      expect(component.sources()).toEqual([]);
      expect(component.formattedSources()).toEqual([]);

      // DOM: Aviso neutro
      const compiled = fixture.nativeElement as HTMLElement;
      const noticeElement = compiled.querySelector('[data-testid="insufficient-context-notice"]');
      expect(noticeElement).toBeTruthy();
      expect(noticeElement?.textContent).toContain('No dispongo de información suficiente');

      // DOM: NO debe existir la sección de fuentes ni tarjetas de fuentes
      const sourcesSection = compiled.querySelector('[data-testid="sources-section"]');
      expect(sourcesSection).toBeNull();
      const sourceCards = compiled.querySelectorAll('[data-testid="source-card"]');
      expect(sourceCards.length).toBe(0);

      // No se guarda en localStorage
      expect(localStorage.length).toBe(0);
    }));

    it('no debe mostrar fuentes incluso si el objeto sources trae elementos por error si hasSufficientContext es false', fakeAsync(() => {
      component.setMode('knowledge');
      fixture.detectChanges();

      const corruptedResponse: KnowledgeAnswer = {
        answer: 'No dispongo de información suficiente.',
        hasSufficientContext: false,
        sources: [
          { sourceId: 'leak', title: 'Fuente filtrada', snippet: 'No debe verse', score: 0.1 }
        ]
      };

      component.askKnowledgeQuestion('consulta irrelevante');
      const req = httpMock.expectOne(askEndpoint);
      req.flush(corruptedResponse);
      tick();
      fixture.detectChanges();

      expect(component.hasSufficientContext()).toBeFalse();
      expect(component.sources()).toEqual([]);

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('[data-testid="sources-section"]')).toBeNull();
    }));
  });

  describe('Escenario 3: Error 503 o timeout con botón de reintentar y texto conservado', () => {
    it('ante error 503 muestra mensaje amigable, botón de reintentar y CONSERVA el texto del campo de entrada', fakeAsync(() => {
      component.setMode('knowledge');
      fixture.detectChanges();

      const questionText = '¿Cuál es el horario de atención para mentorías?';
      const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
      textarea.value = questionText;
      textarea.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      tick();

      // Verificar que el textarea y componente tienen el texto
      expect(textarea.value).toBe(questionText);
      expect(component.knowledgeQuestion()).toBe(questionText);

      // Enviar pregunta
      component.askKnowledgeQuestion();
      fixture.detectChanges();

      const req = httpMock.expectOne(askEndpoint);
      req.flush(
        { title: 'Service Unavailable', status: 503, detail: 'Servicio de IA no disponible temporalmente' },
        { status: 503, statusText: 'Service Unavailable' }
      );
      tick();
      fixture.detectChanges();

      // Signals de error
      expect(component.knowledgeLoading()).toBeFalse();
      expect(component.knowledgeError()).toContain('no está disponible');
      expect(component.knowledgeErrorStatus()).toBe(503);

      // CONSERVA el texto en Signals y en textarea
      expect(component.knowledgeQuestion()).toBe(questionText);
      expect(component.goal).toBe(questionText);
      expect(textarea.value).toBe(questionText);

      // DOM: Muestra alerta amigable y botón de reintentar
      const compiled = fixture.nativeElement as HTMLElement;
      const errorAlert = compiled.querySelector('[data-testid="knowledge-error"]');
      expect(errorAlert).toBeTruthy();
      expect(errorAlert?.textContent).toContain('no está disponible');

      const retryBtn = compiled.querySelector('[data-testid="retry-knowledge-button"]') as HTMLButtonElement;
      expect(retryBtn).toBeTruthy();
      expect(retryBtn.textContent).toContain('Reintentar');

      // Al reintentar, se vuelve a disparar la petición conservando el texto
      retryBtn.click();
      fixture.detectChanges();

      const retryReq = httpMock.expectOne(askEndpoint);
      expect(retryReq.request.body).toEqual({ question: questionText });

      // Responder exitosamente al reintento
      retryReq.flush({
        answer: 'Las mentorías están disponibles de lunes a viernes.',
        hasSufficientContext: true,
        sources: [
          { sourceId: 's1', title: 'Horarios', snippet: 'Lunes a viernes 8am-6pm', score: 0.9 }
        ]
      });
      tick();
      fixture.detectChanges();

      expect(component.knowledgeError()).toBeNull();
      expect(component.knowledgeAnswer()?.answer).toContain('Las mentorías están disponibles');
      expect(compiled.querySelector('[data-testid="knowledge-error"]')).toBeNull();
      expect(compiled.querySelector('[data-testid="sources-section"]')).toBeTruthy();
    }));

    it('ante timeout (status 0) muestra mensaje amigable, botón de reintentar y CONSERVA el texto del campo de entrada', fakeAsync(() => {
      component.setMode('knowledge');
      fixture.detectChanges();

      const questionText = '¿Puedo transferir mi sesión a otro usuario?';
      const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
      textarea.value = questionText;
      textarea.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      tick();

      expect(textarea.value).toBe(questionText);
      expect(component.knowledgeQuestion()).toBe(questionText);

      component.askKnowledgeQuestion();
      fixture.detectChanges();

      const req = httpMock.expectOne(askEndpoint);
      req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
      tick();
      fixture.detectChanges();

      // Signals de error
      expect(component.knowledgeLoading()).toBeFalse();
      expect(component.knowledgeError()).toContain('tiempo de espera');
      expect(component.knowledgeErrorStatus()).toBe(0);

      // Texto conservado
      expect(component.knowledgeQuestion()).toBe(questionText);
      expect(component.goal).toBe(questionText);
      expect(textarea.value).toBe(questionText);

      // DOM: Botón de reintentar presente
      const compiled = fixture.nativeElement as HTMLElement;
      const retryBtn = compiled.querySelector('[data-testid="retry-knowledge-button"]') as HTMLButtonElement;
      expect(retryBtn).toBeTruthy();

      // Invocar retryKnowledge programáticamente
      component.retryKnowledge();
      const retryReq = httpMock.expectOne(askEndpoint);
      expect(retryReq.request.body).toEqual({ question: questionText });
      retryReq.flush({
        answer: 'No es posible transferir sesiones entre cuentas.',
        hasSufficientContext: true,
        sources: []
      });
      tick();
    }));
  });

  describe('Preservación del flujo existente de recomendaciones', () => {
    it('mantiene la llamada a /ai/recommendations en modo recomendación', fakeAsync(() => {
      component.setMode('recommendation');
      component.goal = 'Quiero aprender Spring Boot y Angular';
      fixture.detectChanges();

      component.ask();
      expect(component.loading).toBeTrue();

      const recReq = httpMock.expectOne(`${apiBase()}/ai/recommendations`);
      expect(recReq.request.method).toBe('POST');
      expect(recReq.request.body).toEqual({ goal: 'Quiero aprender Spring Boot y Angular' });

      recReq.flush({
        explanation: 'Te recomendamos comenzar con fundamentos de Spring.',
        inputType: 'TEXT',
        recommendations: [
          { offeringId: 'off-1', score: 0.95, reason: 'Excelente ajuste de perfil' }
        ]
      });
      tick();
      fixture.detectChanges();

      expect(component.loading).toBeFalse();
      expect(component.showModal).toBeTrue();
      expect(component.answer).toBe('Te recomendamos comenzar con fundamentos de Spring.');
      expect(component.recommendations.length).toBe(1);
    }));

    it('muestra el título de la mentoría en cada recomendación', fakeAsync(() => {
      component.setMode('recommendation');
      component.goal = 'Quiero aprender Java';
      fixture.detectChanges();

      component.ask();
      httpMock.expectOne(`${apiBase()}/ai/recommendations`).flush({
        explanation: 'Te sugerimos esta mentoría.',
        inputType: 'TEXT',
        recommendations: [
          { offeringId: 'off-1', offeringTitle: 'Mentoría Java Backend', score: 0.9, reason: 'Cubre Spring Boot' },
          { offeringId: 'off-2', score: 0.5, reason: 'Sin título (respuesta antigua)' }
        ]
      });
      tick();
      fixture.detectChanges();

      const titles = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="recommendation-title"]')
      ).map(el => el.textContent?.trim());
      expect(titles).toEqual(['Mentoría Java Backend']);
    }));
  });
});
