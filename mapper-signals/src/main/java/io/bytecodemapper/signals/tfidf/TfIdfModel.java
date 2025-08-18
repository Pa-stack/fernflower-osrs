// >>> AUTOGEN: BYTECODEMAPPER TfIdfModel BEGIN
package io.bytecodemapper.signals.tfidf;

import java.util.*;

/**
 * Minimal TF-IDF model for Java 8:
 * - Deterministic vocabulary: lexicographically sorted unique terms across docs.
 * - IDF: log((N + 1) / (df + 1)) + 1  (no clamping; keep lightweight for non-micropattern features).
 * - TF: raw counts per doc.
 * - Vectorization: TF * IDF; cosine handled externally.
 */
public final class TfIdfModel {
    private final LinkedHashMap<String,Integer> vocab; // term -> index (sorted lexicographically)
    private final double[] idf; // length = |vocab|
    private final int vocabSize;

    private TfIdfModel(LinkedHashMap<String,Integer> vocab, double[] idf) {
        this.vocab = vocab;
        this.idf = idf;
        this.vocabSize = idf.length;
    }

    public int size() { return vocabSize; }
    public Map<String,Integer> vocab() { return Collections.unmodifiableMap(vocab); }
    public double[] idf() { return Arrays.copyOf(idf, idf.length); }

    /** Build a deterministic model from the given documents (each doc = list of tokens). */
    public static TfIdfModel build(List<List<String>> docs) {
        // 1) Gather unique terms
        SortedSet<String> terms = new TreeSet<String>();
        List<Set<String>> docSets = new ArrayList<Set<String>>(docs.size());
        for (List<String> d : docs) {
            HashSet<String> s = new HashSet<String>(d);
            docSets.add(s);
            terms.addAll(s);
        }
        // 2) Deterministic vocab order
        LinkedHashMap<String,Integer> vocab = new LinkedHashMap<String,Integer>(terms.size());
        int idx = 0;
        for (String t : terms) vocab.put(t, idx++);
        // 3) df and idf
        double[] df = new double[terms.size()];
        for (Set<String> s : docSets) {
            for (String t : s) {
                Integer i = vocab.get(t);
                if (i != null) df[i] += 1.0;
            }
        }
        double N = Math.max(1, docs.size());
        double[] idf = new double[df.length];
        for (int i = 0; i < df.length; i++) {
            idf[i] = Math.log((N + 1.0) / (df[i] + 1.0)) + 1.0;
        }
        return new TfIdfModel(vocab, idf);
    }

    /** Vectorize a doc (list of tokens) into TF*IDF vector. */
    public double[] vectorize(List<String> doc) {
        double[] v = new double[vocabSize];
        if (doc == null || doc.isEmpty()) return v;
        // term frequencies
        HashMap<Integer,Integer> tf = new HashMap<Integer,Integer>();
        for (String t : doc) {
            Integer i = vocab.get(t);
            if (i != null) tf.put(i, tf.getOrDefault(i, 0) + 1);
        }
        for (Map.Entry<Integer,Integer> e : tf.entrySet()) {
            int i = e.getKey();
            v[i] = e.getValue() * idf[i];
        }
        return v;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TfIdfModel END
