package com.riwi.skillbridge.domain.service.knowledge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReciprocalRankFusionTest {

    @Test
    void shouldCalculateExactScoresForTwoRankingsWithThreeDocumentsEach() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        // Two rankings of three documents
        List<String> ranking1 = List.of("docA", "docB", "docC");
        List<String> ranking2 = List.of("docB", "docC", "docD");

        Map<String, Double> scores = rrf.fuse(List.of(ranking1, ranking2));

        // Exact hand-written calculations:
        // docB: rank 2 in ranking1, rank 1 in ranking2
        double expectedDocB = (1.0 / (60 + 2)) + (1.0 / (60 + 1));
        // docC: rank 3 in ranking1, rank 2 in ranking2
        double expectedDocC = (1.0 / (60 + 3)) + (1.0 / (60 + 2));
        // docA: rank 1 in ranking1, absent in ranking2
        double expectedDocA = (1.0 / (60 + 1));
        // docD: absent in ranking1, rank 3 in ranking2
        double expectedDocD = (1.0 / (60 + 3));

        assertEquals(expectedDocB, scores.get("docB"), 1e-9);
        assertEquals(expectedDocC, scores.get("docC"), 1e-9);
        assertEquals(expectedDocA, scores.get("docA"), 1e-9);
        assertEquals(expectedDocD, scores.get("docD"), 1e-9);

        // Verification of ordering (highest to lowest score)
        List<String> orderedDocs = new ArrayList<>(scores.keySet());
        assertEquals(List.of("docB", "docC", "docA", "docD"), orderedDocs);
    }

    @Test
    void shouldScoreDocumentInBothRankingsHigherThanSingleRankingAtSamePosition() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        // docBoth appears at position 1 in both rankings
        // docSingle appears at position 1 in ranking1 only
        List<String> ranking1 = List.of("docBoth", "docSingle");
        List<String> ranking2 = List.of("docBoth", "docOther");

        Map<String, Double> scores = rrf.fuse(List.of(ranking1, ranking2));

        double docBothScore = scores.get("docBoth");
        double docSingleScore = scores.get("docSingle");

        assertTrue(docBothScore > docSingleScore,
                "A document appearing in both rankings must score higher than one appearing in a single ranking");

        // Direct position 1 comparison: docBoth at pos 1 in rA & rB vs docOnlyInA at pos 1 in rA only
        List<String> rA = List.of("docTopBoth", "docOnlyInA");
        List<String> rB = List.of("docTopBoth", "docOnlyInB");

        Map<String, Double> fused = rrf.fuse(List.of(rA, rB));
        assertEquals((1.0 / (60 + 1)) + (1.0 / (60 + 1)), fused.get("docTopBoth"), 1e-9);
        assertEquals(1.0 / (60 + 2), fused.get("docOnlyInA"), 1e-9);
        assertTrue(fused.get("docTopBoth") > fused.get("docOnlyInA"));
    }

    @Test
    void shouldSupportConfigurableKWithDefault60() {
        ReciprocalRankFusion defaultRrf = new ReciprocalRankFusion();
        assertEquals(60, defaultRrf.getK());
        assertEquals(60, defaultRrf.k());

        ReciprocalRankFusion customRrf = new ReciprocalRankFusion(10);
        assertEquals(10, customRrf.getK());
        assertEquals(10, customRrf.k());

        List<String> r1 = List.of("doc1");
        List<String> r2 = List.of("doc1");

        Map<String, Double> defaultScores = defaultRrf.fuse(List.of(r1, r2));
        assertEquals((1.0 / (60 + 1)) + (1.0 / (60 + 1)), defaultScores.get("doc1"), 1e-9);

        Map<String, Double> customScores = customRrf.fuse(List.of(r1, r2));
        assertEquals((1.0 / (10 + 1)) + (1.0 / (10 + 1)), customScores.get("doc1"), 1e-9);
    }

    @Test
    void shouldRejectNegativeK() {
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusion(-1));
    }

    @Test
    void shouldHandleEmptyRankingWithoutBreaking() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        List<String> r1 = List.of("docA", "docB");
        List<String> emptyRanking = List.of();

        Map<String, Double> scores = assertDoesNotThrow(() -> rrf.fuse(List.of(r1, emptyRanking)));

        assertEquals(2, scores.size());
        assertEquals(1.0 / (60 + 1), scores.get("docA"), 1e-9);
        assertEquals(1.0 / (60 + 2), scores.get("docB"), 1e-9);

        // All empty rankings
        Map<String, Double> allEmpty = assertDoesNotThrow(() -> rrf.fuse(List.of(List.of(), List.of())));
        assertTrue(allEmpty.isEmpty());

        // Null list of rankings
        Map<String, Double> nullRankings = assertDoesNotThrow(() -> rrf.fuse(null));
        assertTrue(nullRankings.isEmpty());

        // List containing null ranking
        List<List<String>> listWithNullRanking = new ArrayList<>();
        listWithNullRanking.add(List.of("docA"));
        listWithNullRanking.add(null);
        Map<String, Double> scoresWithNull = assertDoesNotThrow(() -> rrf.fuse(listWithNullRanking));
        assertEquals(1, scoresWithNull.size());
        assertEquals(1.0 / (60 + 1), scoresWithNull.get("docA"), 1e-9);
    }

    @Test
    void shouldDeduplicateDocumentWithinSameRanking() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        // docA appears multiple times in ranking1: only the first occurrence (rank 1) should count
        List<String> ranking1 = List.of("docA", "docB", "docA");
        List<String> ranking2 = List.of("docC");

        Map<String, Double> scores = rrf.fuse(List.of(ranking1, ranking2));

        assertEquals(1.0 / (60 + 1), scores.get("docA"), 1e-9);
        assertEquals(1.0 / (60 + 2), scores.get("docB"), 1e-9);
        assertEquals(1.0 / (60 + 1), scores.get("docC"), 1e-9);
    }

    @Test
    void shouldProvideConvenienceMethodsForTwoRankingsAndRankingList() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();
        List<String> r1 = List.of("docA", "docB");
        List<String> r2 = List.of("docB", "docC");

        Map<String, Double> fusedMap = rrf.fuse(r1, r2);
        List<String> rankedList = rrf.rank(r1, r2);

        assertEquals(List.of("docB", "docA", "docC"), rankedList);
        assertEquals(List.of("docB", "docA", "docC"), new ArrayList<>(fusedMap.keySet()));
    }
}
