// >>> AUTOGEN: BYTECODEMAPPER TinyLikeWriter BEGIN
package io.bytecodemapper.io;

import java.io.*;
import java.util.List;

/** Minimal Tiny-like mapping writer (namespace count=2, header only + class lines). */
public final class TinyLikeWriter {
    private TinyLikeWriter(){}

    public static void writeClasses(File out, List<String[]> classPairs) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));
        try {
            pw.println("tiny\t2\t0\tobf\tdeobf");
            for (String[] p : classPairs) {
                pw.println("c\t" + p[0] + "\t" + p[1]);
            }
        } finally {
            pw.close();
        }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TinyLikeWriter END
