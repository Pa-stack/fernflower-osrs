// >>> AUTOGEN: BYTECODEMAPPER CLI PrintIdf BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.signals.idf.IdfStore;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

final class PrintIdf {

    static void run(String[] args) throws IOException {
        File out = null;
        File from = null;
        Double lambda = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--out".equals(a) && i+1 < args.length) {
                out = new File(args[++i]);
            } else if ("--from".equals(a) && i+1 < args.length) {
                from = new File(args[++i]);
            } else if ("--lambda".equals(a) && i+1 < args.length) {
                lambda = Double.valueOf(args[++i]);
            } else {
                System.err.println("Unknown or incomplete arg: " + a);
            }
        }

        if (out == null) {
            throw new IllegalArgumentException("--out <path> is required");
        }

        IdfStore store = (from != null && from.exists()) ? IdfStore.load(from) : new IdfStore();
        if (lambda != null) store.setLambda(lambda.doubleValue());

        // Ensure IDF is computed and saved
        double[] idf = store.computeIdf();
        System.out.println("IDF (17D): " + Arrays.toString(idf));
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        store.save(out);
        System.out.println("Wrote: " + out.getAbsolutePath());
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI PrintIdf END
