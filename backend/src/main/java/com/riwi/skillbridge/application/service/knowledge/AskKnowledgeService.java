package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.application.port.in.AskKnowledgeUseCase;
import com.riwi.skillbridge.application.port.out.KnowledgeAnswerPort;
import com.riwi.skillbridge.application.port.out.KnowledgeCandidate;
import com.riwi.skillbridge.application.port.out.KnowledgeSearchPort;
import com.riwi.skillbridge.domain.knowledge.KnowledgeAnswer;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import com.riwi.skillbridge.domain.knowledge.KnowledgeSource;
import com.riwi.skillbridge.domain.service.knowledge.ThresholdRule;

import java.util.List;
import java.util.Objects;

/**
 * Servicio de aplicación que implementa el caso de uso de consulta al asistente
 * de conocimiento RAG (HU-26, FR-006, FR-007, FR-010, FR-019).
 * <p>
 * Orquesta el flujo de consulta:
 * <ol>
 *     <li>Sanitización de la pregunta del usuario mediante {@link KnowledgePromptBuilder} (FR-010).</li>
 *     <li>Recuperación de fragmentos candidatos mediante {@link KnowledgeSearchPort}.</li>
 *     <li>Evaluación de umbral mínimo de similitud mediante {@link ThresholdRule} (FR-006, rechazo temprano sin LLM).</li>
 *     <li>Generación de respuesta mediante {@link KnowledgeAnswerPort} con los fragmentos validados.</li>
 *     <li>Validación y conciliación de citas mediante {@link SourceCitationValidator} (FR-007, SC-003).</li>
 * </ol>
 * <p>
 * Clase de aplicación pura sin dependencias de Spring, Jakarta ni Hibernate.
 */
public class AskKnowledgeService implements AskKnowledgeUseCase {

    public static final String NO_INFO_MESSAGE =
            "No dispongo de información suficiente sobre esa consulta en la plataforma.";

    private final KnowledgeSearchPort searchPort;
    private final KnowledgeAnswerPort answerPort;
    private final KnowledgePromptBuilder promptBuilder;
    private final SourceCitationValidator citationValidator;
    private final ThresholdRule thresholdRule;
    private final double minScoreThreshold;
    private final int topK;

    public AskKnowledgeService(
            KnowledgeSearchPort searchPort,
            KnowledgeAnswerPort answerPort,
            KnowledgePromptBuilder promptBuilder,
            SourceCitationValidator citationValidator,
            Double minScoreThreshold,
            int topK
    ) {
        if (minScoreThreshold == null || Double.isNaN(minScoreThreshold)) {
            throw new IllegalStateException("minScoreThreshold no calibrado, ver FR-019");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK debe ser mayor a cero");
        }
        this.searchPort = Objects.requireNonNull(searchPort, "searchPort no puede ser nulo");
        this.answerPort = Objects.requireNonNull(answerPort, "answerPort no puede ser nulo");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder no puede ser nulo");
        this.citationValidator = Objects.requireNonNull(citationValidator, "citationValidator no puede ser nulo");
        this.minScoreThreshold = minScoreThreshold;
        this.topK = topK;
        this.thresholdRule = new ThresholdRule(minScoreThreshold);
    }

    public AskKnowledgeService(
            KnowledgeSearchPort searchPort,
            KnowledgeAnswerPort answerPort,
            Double minScoreThreshold,
            int topK
    ) {
        this(searchPort, answerPort, new KnowledgePromptBuilder(), new SourceCitationValidator(), minScoreThreshold, topK);
    }

    @Override
    public KnowledgeAnswer ask(KnowledgeQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query no puede ser nula");
        }

        // 1. Sanitizar la pregunta vía KnowledgePromptBuilder (FR-010)
        String sanitizedQuestion = promptBuilder.sanitize(query.question());
        if (sanitizedQuestion.isBlank()) {
            throw new IllegalArgumentException("La pregunta no puede estar vacía tras la sanitización");
        }
        KnowledgeQuery sanitizedQuery = new KnowledgeQuery(sanitizedQuestion, query.askedAt(), query.userId());

        // 2. KnowledgeSearchPort.search(query, topK) devuelve KnowledgeCandidate con su similitud coseno
        List<KnowledgeCandidate> candidates = searchPort.search(sanitizedQuery, topK);

        // 3. Umbral (FR-006): si la similitud coseno MÁXIMA de los candidatos es menor que el umbral,
        // devuelve respuesta constante con hasSufficientContext=false y sources vacías SIN llamar a KnowledgeAnswerPort
        if (candidates == null || candidates.isEmpty()) {
            return new KnowledgeAnswer(NO_INFO_MESSAGE, false, List.of());
        }

        double maxCosineSimilarity = candidates.stream()
                .mapToDouble(KnowledgeCandidate::cosineSimilarity)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);

        if (!thresholdRule.isSufficient(maxCosineSimilarity)) {
            return new KnowledgeAnswer(NO_INFO_MESSAGE, false, List.of());
        }

        // 4. Si supera el umbral: KnowledgeAnswerPort.generateAnswer(question, chunks) con los fragmentos validados
        List<KnowledgeCandidate> validatedCandidates = candidates.stream()
                .filter(candidate -> thresholdRule.isSufficient(candidate.cosineSimilarity()))
                .toList();

        List<KnowledgeChunk> validatedChunks = validatedCandidates.stream()
                .map(KnowledgeCandidate::chunk)
                .toList();

        String answerText = answerPort.generateAnswer(sanitizedQuestion, validatedChunks);

        // 5. SourceCitationValidator filtra las fuentes citadas; cada fuente lleva su score normalizado a [0, 1]
        List<KnowledgeSource> candidateSources = validatedCandidates.stream()
                .map(c -> new KnowledgeSource(
                        c.chunk().sourceId(),
                        c.chunk().title(),
                        c.chunk().content(),
                        normalizeScore(c.cosineSimilarity())
                ))
                .collect(java.util.stream.Collectors.toMap(
                        KnowledgeSource::sourceId, x -> x,
                        (a, b) -> a.score() >= b.score() ? a : b,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();

        List<KnowledgeSource> citedSources = citationValidator.filterValidSources(answerText, candidateSources);

        return new KnowledgeAnswer(answerText, true, citedSources);
    }

    public double minScoreThreshold() {
        return minScoreThreshold;
    }

    public double getMinScoreThreshold() {
        return minScoreThreshold;
    }

    public int topK() {
        return topK;
    }

    public int getTopK() {
        return topK;
    }

    private static double normalizeScore(double cosineSimilarity) {
        if (Double.isNaN(cosineSimilarity) || cosineSimilarity < 0.0) {
            return 0.0;
        }
        if (cosineSimilarity > 1.0) {
            return 1.0;
        }
        return cosineSimilarity;
    }
}
