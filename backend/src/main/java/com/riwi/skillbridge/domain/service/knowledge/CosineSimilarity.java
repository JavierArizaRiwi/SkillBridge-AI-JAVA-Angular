package com.riwi.skillbridge.domain.service.knowledge;

import java.util.Arrays;

/**
 * Pure domain service for calculating cosine similarity between two vectors.
 *
 * <p>Restricted to pure Java 21 without external dependencies, Spring, or JPA.
 */
public final class CosineSimilarity {

    public CosineSimilarity() {
    }

    /**
     * Calculates the cosine similarity between two non-null vectors of identical dimension
     * and non-zero norm.
     *
     * @param a the first vector
     * @param b the second vector
     * @return the cosine similarity in the range [-1.0, 1.0]
     * @throws IllegalArgumentException if either vector is null, lengths differ, or either vector has zero norm
     */
    public static double of(double[] a, double[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Vectors must not be null");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors must have the same length: " + a.length + " != " + b.length);
        }
        if (a.length == 0) {
            throw new IllegalArgumentException("Vectors must not be empty (zero norm)");
        }

        double dotProduct = 0.0;
        double normASq = 0.0;
        double normBSq = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normASq += a[i] * a[i];
            normBSq += b[i] * b[i];
        }

        if (normASq == 0.0 || normBSq == 0.0 || Double.isNaN(normASq) || Double.isNaN(normBSq)) {
            throw new IllegalArgumentException("Vector norm cannot be zero or undefined");
        }

        double normA = Math.sqrt(normASq);
        double normB = Math.sqrt(normBSq);
        double denominator = normA * normB;

        if (denominator == 0.0) {
            throw new IllegalArgumentException("Vector norm cannot be zero");
        }

        if (Arrays.equals(a, b)) {
            return 1.0;
        }

        boolean isOpposite = true;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != -b[i]) {
                isOpposite = false;
                break;
            }
        }
        if (isOpposite) {
            return -1.0;
        }

        if (dotProduct == 0.0) {
            return 0.0;
        }

        double similarity = dotProduct / denominator;
        if (similarity > 1.0) {
            return 1.0;
        }
        if (similarity < -1.0) {
            return -1.0;
        }
        return similarity;
    }
}
