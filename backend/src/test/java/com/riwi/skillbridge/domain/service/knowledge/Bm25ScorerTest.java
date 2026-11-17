package com.riwi.skillbridge.domain.service.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25ScorerTest {

    @Test
    @DisplayName("Debe exponer k1 y b como constantes por defecto y permitir configurarlos en el constructor")
    void shouldExposeDefaultParametersAndAllowConfiguration() {
        assertEquals(1.2, Bm25Scorer.DEFAULT_K1, 1e-9);
        assertEquals(0.75, Bm25Scorer.DEFAULT_B, 1e-9);
        assertEquals(1.2, Bm25Scorer.K1, 1e-9);
        assertEquals(0.75, Bm25Scorer.B, 1e-9);

        Bm25Scorer defaultScorer = new Bm25Scorer();
        assertEquals(1.2, defaultScorer.k1(), 1e-9);
        assertEquals(0.75, defaultScorer.b(), 1e-9);
        assertEquals(1.2, defaultScorer.getK1(), 1e-9);
        assertEquals(0.75, defaultScorer.getB(), 1e-9);

        Bm25Scorer customScorer = new Bm25Scorer(1.5, 0.5);
        assertEquals(1.5, customScorer.k1(), 1e-9);
        assertEquals(0.5, customScorer.b(), 1e-9);
        assertEquals(1.5, customScorer.getK1(), 1e-9);
        assertEquals(0.5, customScorer.getB(), 1e-9);

        assertThrows(IllegalArgumentException.class, () -> new Bm25Scorer(-0.1, 0.75));
        assertThrows(IllegalArgumentException.class, () -> new Bm25Scorer(1.2, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new Bm25Scorer(1.2, 1.01));
    }

    /**
     * CÁLCULO MANUAL A MANO (Paso a paso con la fórmula estándar):
     *
     * Corpus de 3 documentos:
     *   D0 = ["java", "spring"] (longitud |D0| = 2)
     *   D1 = ["java", "docker"] (longitud |D1| = 2)
     *   D2 = ["python"]         (longitud |D2| = 1)
     *
     * Total de documentos N = 3
     * Longitud total = 2 + 2 + 1 = 5
     * avgdl = 5 / 3 ≈ 1.6666666666666667
     *
     * Parámetros por defecto: k1 = 1.2, b = 0.75
     *
     * Consulta: Q = ["spring"]
     * El término "spring" solo aparece en D0 -> n("spring") = 1 documento
     *
     * 1. Cálculo de IDF("spring"):
     *    IDF(t) = ln((N - n(t) + 0.5) / (n(t) + 0.5) + 1)
     *           = ln((3 - 1 + 0.5) / (1 + 0.5) + 1)
     *           = ln(2.5 / 1.5 + 1)
     *           = ln(5/3 + 1)
     *           = ln(8/3)
     *           ≈ 0.9808292530117263
     *
     * 2. Componente TF para D0:
     *    tf("spring", D0) = 1
     *    |D0| = 2
     *    |D0| / avgdl = 2 / (5/3) = 6/5 = 1.2
     *    normalización = 1 - b + b * (|D0| / avgdl)
     *                  = 1 - 0.75 + 0.75 * 1.2
     *                  = 0.25 + 0.90 = 1.15
     *    denominador = tf + k1 * normalización
     *                = 1 + 1.2 * 1.15
     *                = 1 + 1.38 = 2.38
     *    numerador   = tf * (k1 + 1)
     *                = 1 * (1.2 + 1) = 2.2
     *    TF_comp     = 2.2 / 2.38 = 110 / 119 ≈ 0.9243697478991597
     *
     * 3. Puntuación BM25 final de D0:
     *    score(D0, Q) = IDF * TF_comp
     *                 = ln(8/3) * (2.2 / 2.38)
     *                 ≈ 0.9808292530117263 * 0.9243697478991597
     *                 ≈ 0.9066488893385707
     *
     * 4. Puntuaciones de D1 y D2:
     *    tf("spring", D1) = 0 -> score(D1, Q) = 0.0
     *    tf("spring", D2) = 0 -> score(D2, Q) = 0.0
     */
    @Test
    @DisplayName("Debe coincidir exactamente con los valores calculados a mano")
    void shouldMatchHandCalculatedValues() {
        Bm25Scorer scorer = new Bm25Scorer();

        List<List<String>> corpus = List.of(
                List.of("java", "spring"), // D0
                List.of("java", "docker"), // D1
                List.of("python")          // D2
        );
        List<String> query = List.of("spring");

        List<Double> scores = scorer.score(corpus, query);

        assertEquals(3, scores.size());
        assertEquals(0.9066488893385707, scores.get(0), 1e-6);
        assertEquals(0.0, scores.get(1), 1e-9);
        assertEquals(0.0, scores.get(2), 1e-9);
    }

    @Test
    @DisplayName("Un documento sin ningún término de la consulta puntúa exactamente 0.0")
    void shouldScoreZeroWhenDocumentContainsNoQueryTerms() {
        Bm25Scorer scorer = new Bm25Scorer();

        List<List<String>> corpus = List.of(
                List.of("angular", "typescript", "frontend"),
                List.of("postgresql", "docker", "devops"),
                List.of("kubernetes", "helm", "cloud")
        );

        List<String> query = List.of("java", "spring");

        List<Double> scores = scorer.score(corpus, query);

        assertAll(
                () -> assertEquals(3, scores.size()),
                () -> assertEquals(0.0, scores.get(0), 1e-9),
                () -> assertEquals(0.0, scores.get(1), 1e-9),
                () -> assertEquals(0.0, scores.get(2), 1e-9)
        );
    }

    @Test
    @DisplayName("Un término raro puntúa más que uno común con la misma frecuencia de término")
    void shouldScoreRareTermHigherThanCommonTerm() {
        Bm25Scorer scorer = new Bm25Scorer();

        // Corpus de 3 documentos donde:
        // "spring" es raro: aparece solo en D0 (n = 1)
        // "java" es común: aparece en D0 y D1 (n = 2)
        // D0 contiene exactamente 1 "spring" y 1 "java".
        List<List<String>> corpus = List.of(
                List.of("java", "spring"), // D0
                List.of("java", "docker"), // D1
                List.of("python")          // D2
        );

        List<Double> scoresRare = scorer.score(corpus, List.of("spring"));
        List<Double> scoresCommon = scorer.score(corpus, List.of("java"));

        double scoreD0Rare = scoresRare.get(0);
        double scoreD0Common = scoresCommon.get(0);

        // IDF("spring") = ln(8/3) ≈ 0.9808
        // IDF("java")   = ln(1.5/2.5 + 1) = ln(1.6) ≈ 0.4700
        // Como TF_comp es idéntico (tf=1 en D0 con la misma longitud), el término raro supera al común.
        assertTrue(scoreD0Rare > scoreD0Common,
                "El término raro ('spring', n=1) debe puntuar más que el común ('java', n=2): "
                        + scoreD0Rare + " vs " + scoreD0Common);
    }

    @Test
    @DisplayName("Repetir un término tiene rendimientos decrecientes (saturación de frecuencia de término)")
    void shouldExhibitDiminishingReturnsWhenRepeatingTerm() {
        Bm25Scorer scorer = new Bm25Scorer();

        // 3 documentos de igual longitud (|D| = 3) para aislar la frecuencia de término (TF):
        // D0 tiene "java" 1 vez (tf = 1)
        // D1 tiene "java" 2 veces (tf = 2)
        // D2 tiene "java" 3 veces (tf = 3)
        List<List<String>> corpus = List.of(
                List.of("java", "foo", "bar"),  // D0: tf=1
                List.of("java", "java", "baz"), // D1: tf=2
                List.of("java", "java", "java") // D2: tf=3
        );

        List<Double> scores = scorer.score(corpus, List.of("java"));

        double s1 = scores.get(0);
        double s2 = scores.get(1);
        double s3 = scores.get(2);

        // 1. Tener más ocurrencias aumenta la puntuación
        assertTrue(s2 > s1, "tf=2 debe superar a tf=1");
        assertTrue(s3 > s2, "tf=3 debe superar a tf=2");

        // 2. Rendimientos decrecientes: duplicar la frecuencia no duplica la puntuación (s2 < 2 * s1)
        assertTrue(s2 < 2 * s1, "Duplicar tf debe tener rendimientos decrecientes (s2 < 2 * s1): "
                + s2 + " < " + (2 * s1));

        // 3. La ganancia marginal disminuye: el incremento de tf 1->2 es mayor que el incremento de tf 2->3
        double gain1to2 = s2 - s1;
        double gain2to3 = s3 - s2;
        assertTrue(gain1to2 > gain2to3,
                "La ganancia marginal debe decrecer: gain(1->2)=" + gain1to2 + " > gain(2->3)=" + gain2to3);
    }

    @Test
    @DisplayName("Con b > 0 un documento más largo se penaliza frente a uno corto con la misma frecuencia")
    void shouldPenalizeLongerDocumentWithSameTermFrequencyWhenBGreaterThanZero() {
        // Con b = 0.75 (por defecto, b > 0)
        Bm25Scorer scorerWithPenalty = new Bm25Scorer(1.2, 0.75);

        // D_short tiene longitud 2 y contiene "java" 1 vez
        // D_long tiene longitud 10 y contiene "java" 1 vez
        // D_filler para tener un corpus de 3 documentos
        List<List<String>> corpus = List.of(
                List.of("java", "spring"),                                                              // D0: corto (|D|=2)
                List.of("java", "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota"), // D1: largo (|D|=10)
                List.of("kotlin", "scala")                                                              // D2: control (|D|=2)
        );

        List<String> query = List.of("java");

        List<Double> scoresWithPenalty = scorerWithPenalty.score(corpus, query);
        double shortScore = scoresWithPenalty.get(0);
        double longScore = scoresWithPenalty.get(1);

        assertTrue(shortScore > longScore,
                "Con b > 0, el documento corto debe puntuar más alto que el largo: "
                        + shortScore + " vs " + longScore);

        // Sin penalización de longitud (b = 0.0), la longitud no afecta la puntuación si tf es igual
        Bm25Scorer scorerWithoutPenalty = new Bm25Scorer(1.2, 0.0);
        List<Double> scoresWithoutPenalty = scorerWithoutPenalty.score(corpus, query);

        assertEquals(scoresWithoutPenalty.get(0), scoresWithoutPenalty.get(1), 1e-6,
                "Con b = 0.0, ambos documentos deben puntuar igual");
    }

    @Test
    @DisplayName("Manejo de casos límite: corpus vacío, query vacía, documentos nulos o términos no encontrados")
    void shouldHandleEdgeCasesGracefully() {
        Bm25Scorer scorer = new Bm25Scorer();

        // Corpus nulo o vacío
        assertTrue(scorer.score(null, List.of("java")).isEmpty());
        assertTrue(scorer.score(List.of(), List.of("java")).isEmpty());

        List<List<String>> corpus = List.of(
                List.of("java", "spring"),
                List.of("python")
        );

        // Query nula o vacía
        assertEquals(List.of(0.0, 0.0), scorer.score(corpus, null));
        assertEquals(List.of(0.0, 0.0), scorer.score(corpus, List.of()));

        // Query con solo términos nulos
        assertEquals(List.of(0.0, 0.0), scorer.score(corpus, java.util.Arrays.asList((String) null)));

        // Término que no existe en el corpus
        assertEquals(List.of(0.0, 0.0), scorer.score(corpus, List.of("rust")));

        // Corpus con documentos vacíos o nulos
        List<List<String>> mixedCorpus = java.util.Arrays.asList(
                null,
                List.of(),
                List.of("java")
        );
        List<Double> mixedScores = scorer.score(mixedCorpus, List.of("java"));
        assertEquals(0.0, mixedScores.get(0), 1e-9);
        assertEquals(0.0, mixedScores.get(1), 1e-9);
        assertTrue(mixedScores.get(2) > 0.0);
    }
}
