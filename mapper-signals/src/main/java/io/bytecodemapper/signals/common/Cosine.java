// >>> AUTOGEN: BYTECODEMAPPER Cosine BEGIN
package io.bytecodemapper.signals.common;

public final class Cosine {
    private Cosine(){}

    public static double cosine(double[] a, double[] b) {
        if (a == null || b == null) return 0.0;
        int n = Math.min(a.length, b.length);
        double dot = 0.0, na2 = 0.0, nb2 = 0.0;
        for (int i = 0; i < n; i++) {
            double x = a[i], y = b[i];
            dot += x * y;
            na2 += x * x;
            nb2 += y * y;
        }
        if (na2 == 0.0 || nb2 == 0.0) return 0.0;
        return dot / (Math.sqrt(na2) * Math.sqrt(nb2));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER Cosine END
