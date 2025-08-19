// >>> AUTOGEN: BYTECODEMAPPER CLI DebugNormalizedDump BEGIN
package io.bytecodemapper.cli.util;

import io.bytecodemapper.cli.method.MethodFeatures;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class DebugNormalizedDump {
    private DebugNormalizedDump(){}

    public static void writeSample(
            java.io.File oldJar, java.io.File newJar,
            Path out, int sample,
            Map<io.bytecodemapper.cli.method.MethodRef, MethodFeatures> oldMf,
            Map<io.bytecodemapper.cli.method.MethodRef, MethodFeatures> newMf) throws java.io.IOException {

        PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out));
        pw.println("# NormalizedMethod debug dump");

        List<MethodFeatures> all = new ArrayList<MethodFeatures>(oldMf.values());
        all.addAll(newMf.values());
        Collections.sort(all, new Comparator<MethodFeatures>() {
            public int compare(MethodFeatures a, MethodFeatures b) {
                int c = a.ref.owner.compareTo(b.ref.owner); if (c!=0) return c;
                c = a.ref.name.compareTo(b.ref.name); if (c!=0) return c;
                return a.ref.desc.compareTo(b.ref.desc);
            }
        });

        int limit = Math.min(sample, all.size());
        for (int i=0;i<limit;i++) {
            MethodFeatures mf = all.get(i);
            pw.println("== " + mf.ref.owner + "#" + mf.ref.name + mf.ref.desc);
            // Field names reflect MethodFeatures fields added earlier
            pw.println("descriptor: " + mf.normalizedDescriptor);
            pw.println("fingerprint: " + mf.normalizedFingerprint);

            // top opcodes by frequency
            int[] h = mf.normOpcodeHistogram;
            List<int[]> pairs = new ArrayList<int[]>();
            for (int op=0; op<h.length; op++) if (h[op] > 0) pairs.add(new int[]{op, h[op]});
            Collections.sort(pairs, new Comparator<int[]>() {
                public int compare(int[] x, int[] y){ return Integer.compare(y[1], x[1]); }
            });
            int N = Math.min(6, pairs.size());
            pw.print("topOpcodes:");
            for (int j=0;j<N;j++) pw.print(" " + pairs.get(j)[0] + "x" + pairs.get(j)[1]);
            pw.println();

            // strings (lexicographic, up to M)
            List<String> ss = new ArrayList<String>(mf.stringBag);
            Collections.sort(ss);
            int M = Math.min(6, ss.size());
            pw.print("strings:");
            for (int j=0;j<M;j++) pw.print(" \"" + ss.get(j).replace("\n","\\n") + "\"");
            pw.println();
        }
        pw.flush(); pw.close();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI DebugNormalizedDump END
