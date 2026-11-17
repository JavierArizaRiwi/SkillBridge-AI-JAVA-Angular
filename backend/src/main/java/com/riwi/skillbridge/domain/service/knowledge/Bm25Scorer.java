package com.riwi.skillbridge.domain.service.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java implementation of the BM25 (Best Matching 25) scoring function.
 *
 * <p>Formula:
 * <pre>
 * score(D, Q) = &Sigma; IDF(t) &middot; (tf(t, D) &middot; (k1 + 1)) / (tf(t, D) + k1 &middot; (1 - b + b &middot; (|D| / avgdl)))
 * </pre>
 * where IDF is the standard Lucene/Robertson BM25 inverse document frequency:
 * <pre>
 * IDF(t) = ln((N - n(t) + 0.5) / (n(t) + 0.5) + 1)
 * </pre>
 *
 * <p>Tokenization is not handled here; caller is responsible for tokenizing documents and queries.
 * Pure domain service: no Spring or external library dependencies.
 */
public class Bm25Scorer {

    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;
    public static final double K1 = DEFAULT_K1;
    public static final double B = DEFAULT_B;

    private final double k1;
    private final double b;

    /**
     * Creates a Bm25Scorer with default parameters (k1 = 1.2, b = 0.75).
     */
    public Bm25Scorer() {
        this(DEFAULT_K1, DEFAULT_B);
    }

    /**
     * Creates a Bm25Scorer with custom k1 and b parameters.
     *
     * @param k1 term frequency saturation parameter (k1 &gt;= 0)
     * @param b  document length normalization parameter (0.0 &lt;= b &lt;= 1.0)
     */
    public Bm25Scorer(double k1, double b) {
        if (k1 < 0.0) {
            throw new IllegalArgumentException("k1 must be non-negative");
        }
        if (b < 0.0 || b > 1.0) {
            throw new IllegalArgumentException("b must be in the range [0.0, 1.0]");
        }
        this.k1 = k1;
        this.b = b;
    }

    public double k1() {
        return k1;
    }

    public double b() {
        return b;
    }

    public double getK1() {
        return k1;
    }

    public double getB() {
        return b;
    }

    /**
     * Computes the standard BM25 inverse document frequency (IDF) for a term.
     *
     * @param totalDocuments    total number of documents in corpus (N)
     * @param documentFrequency number of documents containing the term (n)
     * @return IDF score
     */
    public static double idf(int totalDocuments, int documentFrequency) {
        if (totalDocuments <= 0 || documentFrequency < 0 || documentFrequency > totalDocuments) {
            throw new IllegalArgumentException("Invalid document frequency or total documents");
        }
        return Math.log(((totalDocuments - documentFrequency + 0.5) / (documentFrequency + 0.5)) + 1.0);
    }

    /**
     * Computes the BM25 score of each document in the corpus for the given query.
     *
     * @param corpus tokenized documents, where each document is a list of terms
     * @param query  tokenized query as a list of terms
     * @return list of BM25 scores aligned with the corpus indices (unmodifiable)
     */
    public List<Double> score(List<? extends List<String>> corpus, List<String> query) {
        if (corpus == null || corpus.isEmpty()) {
            return List.of();
        }

        int numDocs = corpus.size();
        if (query == null || query.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(Collections.nCopies(numDocs, 0.0)));
        }

        List<String> validQueryTerms = new ArrayList<>();
        Set<String> distinctQueryTerms = new HashSet<>();
        for (String term : query) {
            if (term != null) {
                validQueryTerms.add(term);
                distinctQueryTerms.add(term);
            }
        }

        if (validQueryTerms.isEmpty() || distinctQueryTerms.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(Collections.nCopies(numDocs, 0.0)));
        }

        // 1. Calculate document lengths and average document length (avgdl)
        int[] docLengths = new int[numDocs];
        long totalLength = 0;
        for (int i = 0; i < numDocs; i++) {
            List<String> doc = corpus.get(i);
            int len = (doc != null) ? doc.size() : 0;
            docLengths[i] = len;
            totalLength += len;
        }

        if (totalLength == 0) {
            return Collections.unmodifiableList(new ArrayList<>(Collections.nCopies(numDocs, 0.0)));
        }

        double avgdl = (double) totalLength / numDocs;

        // 2. Count term frequencies per document (only for query terms) and document frequency n(t)
        List<Map<String, Integer>> docTermFreqs = new ArrayList<>(numDocs);
        Map<String, Integer> docFrequencies = new HashMap<>();

        for (int i = 0; i < numDocs; i++) {
            List<String> doc = corpus.get(i);
            Map<String, Integer> freqMap = new HashMap<>();
            if (doc != null) {
                for (String term : doc) {
                    if (term != null && distinctQueryTerms.contains(term)) {
                        freqMap.merge(term, 1, Integer::sum);
                    }
                }
            }
            for (String term : freqMap.keySet()) {
                docFrequencies.merge(term, 1, Integer::sum);
            }
            docTermFreqs.add(freqMap);
        }

        // 3. Accumulate BM25 score per document across query terms
        double[] scores = new double[numDocs];

        for (String qTerm : validQueryTerms) {
            int n = docFrequencies.getOrDefault(qTerm, 0);
            if (n == 0) {
                continue;
            }

            double idf = idf(numDocs, n);

            for (int i = 0; i < numDocs; i++) {
                int tf = docTermFreqs.get(i).getOrDefault(qTerm, 0);
                if (tf == 0) {
                    continue;
                }

                double docLen = docLengths[i];
                double numerator = tf * (k1 + 1.0);
                double denominator = tf + k1 * (1.0 - b + b * (docLen / avgdl));
                scores[i] += idf * (numerator / denominator);
            }
        }

        List<Double> result = new ArrayList<>(numDocs);
        for (double s : scores) {
            result.add(s);
        }
        return Collections.unmodifiableList(result);
    }
}
