package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.application.port.out.KnowledgeAnswerPort;
import com.riwi.skillbridge.application.port.out.KnowledgeCandidate;
import com.riwi.skillbridge.application.port.out.KnowledgeSearchPort;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.service.knowledge.CosineSimilarity;
import com.riwi.skillbridge.domain.service.knowledge.HybridRanker;
import com.riwi.skillbridge.infrastructure.adapter.out.knowledge.ClasspathKnowledgeCorpusSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Dobles deterministas para pruebas sin red, sin claves y sin Docker (SC-007).
 * El "embedding" es una bolsa de palabras con hashing (prefijo de 5 letras): no es semántico real,
 * por lo que las cifras que produce solo validan el cableado y el comportamiento, no la calidad con Gemini.
 */
public final class DeterministicKnowledgeFixture {

    public static final int DIMS = 1024;

    private static final java.util.Set<String> STOP = java.util.Set.of(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "que", "en", "y", "o", "con", "por", "para",
            "se", "es", "del", "al", "lo", "mi", "me", "mis", "tu", "su", "sus", "otros", "otras", "dame", "como",
            "cuanta", "cuanto", "puedo", "debo", "hay", "son", "ser", "si", "no", "te", "le", "les", "ya", "mas",
            "the", "an", "of", "to", "in", "and", "or", "is", "are", "do", "does", "my", "how", "much", "can", "it",
            "for", "on", "with", "as", "be", "by", "at", "what", "when", "need", "any", "your", "you", "ignora",
            "todas", "previas");

    private DeterministicKnowledgeFixture() { }

    public static List<KnowledgeChunk> corpus() {
        return new KnowledgeCorpusLoader(new ClasspathKnowledgeCorpusSource()).load();
    }

    public static boolean isZero(double[] v) {
        for (double x : v) {
            if (x != 0.0) {
                return false;
            }
        }
        return true;
    }

    public static double[] embed(String text) {
        double[] v = new double[DIMS];
        for (String t : HybridRanker.tokenize(text)) {
            if (STOP.contains(t)) {
                continue;
            }
            String stem = t.length() > 5 ? t.substring(0, 5) : t;
            v[Math.floorMod(stem.hashCode(), DIMS)] += 1.0;
        }
        return v;
    }

    /** Búsqueda en memoria sobre el corpus real con la estrategia indicada. */
    public static class InMemorySearch implements KnowledgeSearchPort {
        final List<KnowledgeChunk> chunks;
        final HybridRanker.Strategy strategy;
        final EmbeddingPort embedder;

        public InMemorySearch(List<KnowledgeChunk> chunks, HybridRanker.Strategy strategy, EmbeddingPort embedder) {
            this.chunks = chunks;
            this.strategy = strategy;
            this.embedder = embedder;
        }

        public InMemorySearch(List<KnowledgeChunk> chunks, HybridRanker.Strategy strategy) {
            this(chunks, strategy, texts -> texts.stream().map(DeterministicKnowledgeFixture::embed).toList());
        }

        @Override
        public List<KnowledgeCandidate> search(String query, int topK) {
            double[] q = embedder.embed(query);
            List<String> docs = new ArrayList<>();
            List<Double> cos = new ArrayList<>();
            for (KnowledgeChunk c : chunks) {
                String text = c.title() + " " + c.content();
                docs.add(text);
                double[] d = embed(text);
                cos.add(isZero(q) || isZero(d) ? 0.0 : CosineSimilarity.of(q, d));
            }
            return new HybridRanker(60).rank(docs, cos, query, strategy).stream()
                    .limit(topK)
                    .map(i -> new KnowledgeCandidate(chunks.get(i), cos.get(i)))
                    .toList();
        }
    }

    /** LLM falso: responde citando los sourceId recibidos; cuenta las llamadas (espía). */
    public static class SpyAnswerPort implements KnowledgeAnswerPort {
        public final AtomicInteger calls = new AtomicInteger();
        public volatile long delayMillis = 0;
        public volatile RuntimeException failure;

        @Override
        public String generateAnswer(String question, List<KnowledgeChunk> validatedChunks) {
            calls.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
            String ids = validatedChunks.stream().map(c -> "[" + c.sourceId() + "]").distinct()
                    .collect(Collectors.joining(" "));
            return "Respuesta basada en el contexto " + ids;
        }
    }
}
