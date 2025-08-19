// >>> AUTOGEN: BYTECODEMAPPER CLI BenchPairs BEGIN
package io.bytecodemapper.cli.bench;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class BenchPairs {
    private BenchPairs(){}

    // Pair representation (old -> new) with human tag "old→new"
    public static final class BenchPair {
        public final Path oldJar;
        public final Path newJar;
        public final String tag;
        public BenchPair(Path oldJar, Path newJar) {
            this.oldJar = oldJar; this.newJar = newJar;
            this.tag = oldJar.getFileName().toString() + "→" + newJar.getFileName().toString();
        }
    }

    public static List<BenchPair> buildFromDirectory(Path dir) throws IOException {
        List<Path> jars = new ArrayList<Path>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path p : ds) jars.add(p.toAbsolutePath());
        }
        // Sort ascending by numeric suffix if present; otherwise lex asc
        Collections.sort(jars, new Comparator<Path>() {
            public int compare(Path a, Path b) {
                int na = extractNum(a.getFileName().toString());
                int nb = extractNum(b.getFileName().toString());
                if (na != -1 && nb != -1 && na != nb) return Integer.compare(na, nb);
                return a.getFileName().toString().compareTo(b.getFileName().toString());
            }
        });
        List<BenchPair> out = new ArrayList<BenchPair>(Math.max(0, jars.size() - 1));
        for (int i = 0; i + 1 < jars.size(); i++) {
            Path oldJar = jars.get(i);
            Path newJar = jars.get(i + 1);
            out.add(new BenchPair(oldJar, newJar));
        }
        return out;
    }

    private static int extractNum(String name) {
        // matches osrs-<num>.jar ; returns -1 if not parsable
        int dash = name.lastIndexOf('-');
        int dot = name.lastIndexOf('.');
        if (dash < 0 || dot < 0 || dot <= dash) return -1;
        try {
            return Integer.parseInt(name.substring(dash + 1, dot));
        } catch (Exception e) {
            return -1;
        }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI BenchPairs END
