// >>> AUTOGEN: BYTECODEMAPPER IdfStore BEGIN
package io.bytecodemapper.signals.idf;

import io.bytecodemapper.signals.micro.MicroPattern;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Properties;

public final class IdfStore {
    private static final double MIN = 0.5, MAX = 3.0;
    private static final DecimalFormat DF4 = new DecimalFormat("#.####");

    private final double[] idf = new double[MicroPattern.values().length];

    public IdfStore() {
        Arrays.fill(idf, 1.0);
    }

    public double[] get() { return Arrays.copyOf(idf, idf.length); }

    public void computeEma(double[] dfWeighted, double methodsWeighted) {
        for (int i = 0; i < idf.length; i++) {
            double val = Math.log((methodsWeighted + 1.0) / (dfWeighted[i] + 1.0)) + 1.0;
            if (val < MIN) val = MIN;
            if (val > MAX) val = MAX;
            idf[i] = Double.valueOf(DF4.format(val));
        }
    }

    public void save(File f, String meta) throws IOException {
        Properties p = new Properties();
        for (int i = 0; i < idf.length; i++) {
            p.setProperty("idf." + i, DF4.format(idf[i]));
        }
        p.setProperty("meta", meta == null ? "" : meta);
        FileOutputStream fos = new FileOutputStream(f);
        try { p.store(fos, "Micropattern IDF (clamped, 4dp)"); }
        finally { fos.close(); }
    }

    public void load(File f) throws IOException {
        if (!f.exists()) return;
        Properties p = new Properties();
        FileInputStream fis = new FileInputStream(f);
        try { p.load(fis); }
        finally { fis.close(); }
        for (int i = 0; i < idf.length; i++) {
            String v = p.getProperty("idf." + i);
            if (v != null) idf[i] = Double.parseDouble(v);
        }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER IdfStore END
