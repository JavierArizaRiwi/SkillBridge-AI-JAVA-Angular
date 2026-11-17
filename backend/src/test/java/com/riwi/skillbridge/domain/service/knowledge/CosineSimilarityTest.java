package com.riwi.skillbridge.domain.service.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CosineSimilarity Domain Service")
class CosineSimilarityTest {

    @Test
    @DisplayName("Should return 1.0 for identical vectors")
    void shouldReturnOneForIdenticalVectors() {
        assertEquals(1.0, CosineSimilarity.of(new double[]{1.0}, new double[]{1.0}));
        assertEquals(1.0, CosineSimilarity.of(new double[]{1.0, 2.0, 3.0}, new double[]{1.0, 2.0, 3.0}));
        assertEquals(1.0, CosineSimilarity.of(new double[]{-0.5, 0.25, 0.75}, new double[]{-0.5, 0.25, 0.75}));
    }

    @Test
    @DisplayName("Should return 0.0 for orthogonal vectors")
    void shouldReturnZeroForOrthogonalVectors() {
        assertEquals(0.0, CosineSimilarity.of(new double[]{1.0, 0.0}, new double[]{0.0, 1.0}));
        assertEquals(0.0, CosineSimilarity.of(new double[]{1.0, 0.0, 0.0}, new double[]{0.0, 1.0, 0.0}));
        assertEquals(0.0, CosineSimilarity.of(new double[]{1.0, -1.0}, new double[]{1.0, 1.0}));
    }

    @Test
    @DisplayName("Should return -1.0 for opposite vectors")
    void shouldReturnMinusOneForOppositeVectors() {
        assertEquals(-1.0, CosineSimilarity.of(new double[]{1.0, 0.0}, new double[]{-1.0, 0.0}));
        assertEquals(-1.0, CosineSimilarity.of(new double[]{1.0, 2.0, 3.0}, new double[]{-1.0, -2.0, -3.0}));
        assertEquals(-1.0, CosineSimilarity.of(new double[]{-0.5, 0.25}, new double[]{0.5, -0.25}));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when vector lengths differ")
    void shouldThrowIllegalArgumentExceptionWhenVectorLengthsDiffer() {
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(new double[]{1.0, 2.0}, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(new double[]{1.0}, new double[]{1.0, 2.0, 3.0}));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when either or both vectors are null")
    void shouldThrowIllegalArgumentExceptionWhenVectorsAreNull() {
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(null, new double[]{1.0, 2.0}));
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(new double[]{1.0, 2.0}, null));
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(null, null));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when vector has zero length")
    void shouldThrowIllegalArgumentExceptionWhenVectorIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(new double[]{}, new double[]{}));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when either or both vectors have zero norm")
    void shouldThrowIllegalArgumentExceptionWhenVectorHasZeroNorm() {
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(new double[]{0.0, 0.0}, new double[]{1.0, 2.0}));
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(new double[]{1.0, 2.0}, new double[]{0.0, 0.0}));
        assertThrows(IllegalArgumentException.class, () ->
                CosineSimilarity.of(new double[]{0.0, 0.0}, new double[]{0.0, 0.0}));
    }

    @Test
    @DisplayName("Should compute intermediate cosine similarity values accurately")
    void shouldComputeIntermediateValuesAccurately() {
        // Angle 60 degrees -> cos(pi/3) = 0.5
        double[] a = {1.0, 0.0};
        double[] b = {0.5, Math.sqrt(3.0) / 2.0};
        assertEquals(0.5, CosineSimilarity.of(a, b), 1e-9);
    }

    @Test
    @DisplayName("Should allow instance invocation when instantiated")
    void shouldAllowInstanceInvocation() {
        CosineSimilarity cs = new CosineSimilarity();
        assertEquals(1.0, cs.of(new double[]{1.0}, new double[]{1.0}));
    }
}
