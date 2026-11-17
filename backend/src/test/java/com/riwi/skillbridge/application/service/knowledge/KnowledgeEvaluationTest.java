package com.riwi.skillbridge.application.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.port.out.KnowledgeCandidate;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.service.knowledge.HybridRanker;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T029: informe de evaluación hit@k, MRR y falsos positivos para BM25 solo, embeddings solos e híbrida.
 * Usa el embedding determinista (bolsa de palabras con hashing, sin semántica real ni cruce ES-EN),
 * por lo que las cifras NO representan la calidad con Gemini.
 * Escribe specs/026-rag-knowledge/evaluation-report.md.
 */
class KnowledgeEvaluationTest {

    private static final double THRESHOLD = 0.5;
    private static final int K = 3;

    record Metrics(double hitAtK, double mrr, int falsePositives, int unanswerable, int answerable) { }

    @Test
    void generaInformeConTresEstrategias() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/knowledge-eval/questions.json")) {
            root = new ObjectMapper().readTree(in);
        }
        JsonNode questions = root.get("questions");
        assertTrue(questions.size() >= 30);
        List<KnowledgeChunk> corpus = DeterministicKnowledgeFixture.corpus();

        StringBuilder rows = new StringBuilder();
        Metrics any = null;
        for (HybridRanker.Strategy s : HybridRanker.Strategy.values()) {
            Metrics m = evaluate(questions, new DeterministicKnowledgeFixture.InMemorySearch(corpus, s));
            any = m;
            rows.append(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %d / %d |%n",
                    s, m.hitAtK(), m.mrr(), m.falsePositives(), m.unanswerable()));
        }

        String report = String.format(Locale.ROOT, """
                # Informe de evaluación — HU-26 (generado por KnowledgeEvaluationTest)

                > **Cómo leerlo:** ejecución con dobles deterministas (embedding de bolsa de palabras con hashing, sin red ni Gemini).
                > Mide el cableado y la fusión, **no** la calidad semántica real. Para calibrar `minScoreThreshold` y `rrfK` (T030)
                > hay que repetirlo con Gemini. El embedding determinista no cruza ES-EN: las preguntas en inglés
                > solo aciertan por coincidencia léxica con la sección EN.

                Parámetros: k = %d, umbral coseno = %.2f, rrfK = 60, preguntas = %d (respondibles: %d, sin respuesta: %d).

                | Estrategia | hit@%d | MRR | Falsos positivos |
                |---|---|---|---|
                %s
                Definiciones: hit@k = fracción de preguntas respondibles con algún fragmento esperado entre los k primeros;
                MRR = media del inverso del rango del primer acierto; falso positivo = pregunta sin respuesta cuya mejor
                similitud coseno supera el umbral (el sistema llamaría al LLM).
                """, K, THRESHOLD, questions.size(), any.answerable(), any.unanswerable(), K, rows);
        Path out = Path.of("..", "specs", "026-rag-knowledge", "evaluation-report.md");
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private Metrics evaluate(JsonNode questions, DeterministicKnowledgeFixture.InMemorySearch search) {
        int answerable = 0, hits = 0, falsePos = 0, unanswerable = 0;
        double rrSum = 0;
        for (JsonNode q : questions) {
            List<KnowledgeCandidate> res = search.search(q.get("question").asText(), K);
            if (q.get("answerable").asBoolean()) {
                answerable++;
                List<String> expected = new ArrayList<>();
                q.get("expectedSourceIds").forEach(n -> expected.add(n.asText()));
                for (int i = 0; i < res.size(); i++) {
                    if (expected.contains(res.get(i).chunk().sourceId())) {
                        hits++;
                        rrSum += 1.0 / (i + 1);
                        break;
                    }
                }
            } else {
                unanswerable++;
                double max = res.stream().mapToDouble(KnowledgeCandidate::cosineSimilarity).max().orElse(0);
                if (max >= THRESHOLD) {
                    falsePos++;
                }
            }
        }
        return new Metrics((double) hits / answerable, rrSum / answerable, falsePos, unanswerable, answerable);
    }
}
