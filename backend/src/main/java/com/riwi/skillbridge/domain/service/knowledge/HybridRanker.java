package com.riwi.skillbridge.domain.service.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Ordena documentos combinando BM25 (léxico) y similitud coseno (semántico) con Reciprocal Rank Fusion (HU-26, R6).
 * Dominio puro: recibe textos y similitudes ya calculadas, devuelve índices ordenados.
 */
public final class HybridRanker {

    public enum Strategy { BM25_ONLY, EMBEDDINGS_ONLY, HYBRID }

    private final Bm25Scorer bm25 = new Bm25Scorer();
    private final ReciprocalRankFusion rrf;

    public HybridRanker(int rrfK) {
        this.rrf = new ReciprocalRankFusion(rrfK);
    }

    /**
     * @param documents    texto de cada documento
     * @param cosines      similitud coseno de cada documento con la consulta (mismo orden)
     * @return índices de documentos del mejor al peor (BM25 omite documentos con puntuación 0)
     */
    public List<Integer> rank(List<String> documents, List<Double> cosines, String query, Strategy strategy) {
        int n = documents.size();
        if (n != cosines.size()) {
            throw new IllegalArgumentException("documents y cosines deben tener el mismo tamaño");
        }
        List<Integer> vectorRanking = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            vectorRanking.add(i);
        }
        vectorRanking.sort((a, b) -> Double.compare(cosines.get(b), cosines.get(a)));

        List<List<String>> corpus = new ArrayList<>(n);
        for (String d : documents) {
            corpus.add(tokenize(d));
        }
        List<Double> scores = bm25.score(corpus, tokenize(query));
        List<Integer> bm25Ranking = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (scores.get(i) > 0.0) {
                bm25Ranking.add(i);
            }
        }
        bm25Ranking.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

        return switch (strategy) {
            case BM25_ONLY -> bm25Ranking;
            case EMBEDDINGS_ONLY -> vectorRanking;
            case HYBRID -> rrf.rank(vectorRanking, bm25Ranking);
        };
    }

    public static List<String> tokenize(String text) {
        if (text == null) {
            return List.of();
        }
        String folded = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        List<String> tokens = new ArrayList<>();
        for (String t : folded.split("[^a-z0-9]+")) {
            if (t.length() > 1) {
                tokens.add(t);
            }
        }
        return tokens;
    }
}
