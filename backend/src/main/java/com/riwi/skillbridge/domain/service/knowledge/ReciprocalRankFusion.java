package com.riwi.skillbridge.domain.service.knowledge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure Java implementation of Reciprocal Rank Fusion (RRF) for hybrid retrieval.
 * <p>
 * Combines ranked lists of document identifiers using the formula:
 * {@code score(d) = Σ 1 / (k + rank_r(d))}
 * where rank starts at 1, and {@code k} is a configurable constant (default 60, ADR-0008).
 * </p>
 */
public final class ReciprocalRankFusion {

    public static final int DEFAULT_K = 60;

    private final int k;

    public ReciprocalRankFusion() {
        this(DEFAULT_K);
    }

    public ReciprocalRankFusion(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("k must be greater than or equal to 0, got: " + k);
        }
        this.k = k;
    }

    public int getK() {
        return k;
    }

    public int k() {
        return k;
    }

    /**
     * Fuses multiple ranked lists into a single map of document identifiers
     * to fused scores, ordered descending by score.
     *
     * @param rankings list of ranked lists of document identifiers
     * @param <T>      document identifier type
     * @return map of document identifier to fused score, sorted from highest to lowest score
     */
    public <T> Map<T, Double> fuse(List<? extends List<T>> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<T, Double> rawScores = new LinkedHashMap<>();
        for (List<T> ranking : rankings) {
            if (ranking == null || ranking.isEmpty()) {
                continue;
            }
            Set<T> seenInRanking = new HashSet<>();
            for (int i = 0; i < ranking.size(); i++) {
                T doc = ranking.get(i);
                if (doc == null) {
                    continue;
                }
                if (seenInRanking.add(doc)) {
                    int rank = i + 1;
                    double reciprocalScore = 1.0 / (k + rank);
                    rawScores.merge(doc, reciprocalScore, Double::sum);
                }
            }
        }

        return rawScores.entrySet().stream()
                .sorted(Map.Entry.<T, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    /**
     * Convenience method to fuse two ranked lists.
     *
     * @param ranking1 first ranked list
     * @param ranking2 second ranked list
     * @param <T>      document identifier type
     * @return map of document identifier to fused score, sorted from highest to lowest score
     */
    public <T> Map<T, Double> fuse(List<T> ranking1, List<T> ranking2) {
        List<List<T>> list = new ArrayList<>(2);
        if (ranking1 != null) {
            list.add(ranking1);
        }
        if (ranking2 != null) {
            list.add(ranking2);
        }
        return fuse(list);
    }

    /**
     * Convenience method returning the fused ranked document identifiers in order of score.
     *
     * @param rankings list of ranked lists of document identifiers
     * @param <T>      document identifier type
     * @return list of document identifiers ordered from highest to lowest score
     */
    public <T> List<T> rank(List<? extends List<T>> rankings) {
        return new ArrayList<>(fuse(rankings).keySet());
    }

    /**
     * Convenience method returning the fused ranked document identifiers of two rankings in order of score.
     *
     * @param ranking1 first ranked list
     * @param ranking2 second ranked list
     * @param <T>      document identifier type
     * @return list of document identifiers ordered from highest to lowest score
     */
    public <T> List<T> rank(List<T> ranking1, List<T> ranking2) {
        return new ArrayList<>(fuse(ranking1, ranking2).keySet());
    }
}
