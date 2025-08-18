// >>> AUTOGEN: BYTECODEMAPPER IdfStore BEGIN
package io.bytecodemapper.signals.idf;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Properties;

/**
 * 17D IDF store for micropatterns with an exponential moving accumulator.
 * Policy:
 *   DF_t(p) ~= lambda * DF_{t-1}(p) + df_week(p)
 *   M_t      ~= lambda * M_{t-1}     + totalMethods_week
 *   IDF(p) = clamp( log((M+1)/(DF(p)+1)) + 1 , [0.5, 3.0] ), rounded to 4 dp
 *
 * Defaults: lambda = 0.9
 * Notes: When cold-start (M == 0), IDF defaults to 1.0.
 */
public final class IdfStore {

    public static final int DIM = 17;

    private double lambda = 0.9d;           // decay
    private double mEma = 0.0d;             // total methods EMA
    private final double[] dfEma = new double[DIM]; // per-pattern DF EMA
    private final double[] idf = new double[DIM];   // last computed IDF

    public IdfStore() {
        Arrays.fill(idf, 1.0d);
    }

    public double getLambda() { return lambda; }
    public void setLambda(double lambda) {
        if (lambda <= 0 || lambda >= 1) throw new IllegalArgumentException("lambda in (0,1)");
        this.lambda = lambda;
    }

    /** Update accumulators with one week's counts. */
    public void updateWeek(int totalMethods, int[] dfCountsPerBit) {
        if (dfCountsPerBit == null || dfCountsPerBit.length != DIM)
            throw new IllegalArgumentException("dfCountsPerBit must be length " + DIM);
        if (totalMethods < 0) throw new IllegalArgumentException("totalMethods >= 0");
        mEma = lambda * mEma + totalMethods;
        for (int i = 0; i < DIM; i++) {
            int df = Math.max(0, dfCountsPerBit[i]);
            dfEma[i] = lambda * dfEma[i] + df;
        }
    }

    /** Compute the IDF vector per policy; clamp to [0.5, 3.0] and round to 4 dp. */
    public double[] computeIdf() {
        final double M = mEma;
        if (M <= 0.0) {
            Arrays.fill(idf, 1.0d);
            return Arrays.copyOf(idf, DIM);
        }
        for (int i = 0; i < DIM; i++) {
            double DF = dfEma[i];
            double v = Math.log((M + 1.0d) / (DF + 1.0d)) + 1.0d;
            v = clamp(v, 0.5d, 3.0d);
            idf[i] = round4(v);
        }
        return Arrays.copyOf(idf, DIM);
    }

    public double[] getIdfVector() {
        return Arrays.copyOf(idf, DIM);
    }

    public double getMEma() { return mEma; }
    public double[] getDfEma() { return Arrays.copyOf(dfEma, DIM); }

    /** Save as .properties for easy inspection. */
    public void save(File file) throws IOException {
        Properties p = new Properties();
        p.setProperty("lambda", Double.toString(lambda));
        p.setProperty("M_ema", Double.toString(mEma));
        for (int i = 0; i < DIM; i++) p.setProperty("DF_ema." + i, Double.toString(dfEma[i]));
        // ensure IDF is up to date in the file
        double[] cur = computeIdf();
        for (int i = 0; i < DIM; i++) p.setProperty("idf." + i, format4(cur[i]));
        try (OutputStream os = new FileOutputStream(file)) {
            p.store(os, "BytecodeMapper Micropattern IDF (EMA)");
        }
    }

    /** Load from .properties, missing keys use defaults. */
    public static IdfStore load(File file) throws IOException {
        IdfStore s = new IdfStore();
        if (!file.exists()) return s;
        Properties p = new Properties();
        try (InputStream is = new FileInputStream(file)) {
            p.load(is);
        }
        String lam = p.getProperty("lambda");
        if (lam != null) s.setLambda(Double.parseDouble(lam));
        String M = p.getProperty("M_ema");
        if (M != null) s.mEma = Double.parseDouble(M);
        for (int i = 0; i < DIM; i++) {
            String d = p.getProperty("DF_ema." + i);
            if (d != null) s.dfEma[i] = Double.parseDouble(d);
            String iv = p.getProperty("idf." + i);
            if (iv != null) s.idf[i] = Double.parseDouble(iv);
        }
        // recompute idf to reflect DF/M; keep persisted idf as a hint
        s.computeIdf();
        return s;
    }

    // --- helpers ---

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round4(double v) {
        return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String format4(double v) {
        return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER IdfStore END
