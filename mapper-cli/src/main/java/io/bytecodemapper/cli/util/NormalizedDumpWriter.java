// >>> AUTOGEN: BYTECODEMAPPER CLI NormalizedDumpWriter BEGIN
package io.bytecodemapper.cli.util;

import io.bytecodemapper.core.fingerprint.ClasspathScanner;
import io.bytecodemapper.signals.normalized.NormalizedMethod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Writes per-method NormalizedMethod feature bundle as JSONL deterministically.
 * Java 8 compatible, no external JSON deps. Stable key order.
 */
public final class NormalizedDumpWriter implements Opcodes {
    private NormalizedDumpWriter() {}

    public static void dumpJsonl(Path oldJar, Path newJar, Path outDir) throws IOException {
        // Resolve inputs deterministically
        Map<String, ClassNode> oldClasses = readJar(oldJar);
        Map<String, ClassNode> newClasses = readJar(newJar);

        // Compose outputs: old.jsonl and new.jsonl
        Path oldOut = outDir.resolve("old.jsonl");
        Path newOut = outDir.resolve("new.jsonl");
        writeSide(oldClasses, oldOut, true);
        writeSide(newClasses, newOut, false);
    }

    private static Map<String, ClassNode> readJar(Path jar) throws IOException {
        final Map<String, ClassNode> map = new TreeMap<String, ClassNode>();
        ClasspathScanner scanner = new ClasspathScanner();
        scanner.scan(jar.toFile(), new ClasspathScanner.Sink() {
            public void accept(ClassNode cn) { map.put(cn.name, cn); }
        });
        return map;
    }

    private static void writeSide(Map<String, ClassNode> classes, Path outFile, boolean isOld) throws IOException {
        if (outFile.getParent()!=null) Files.createDirectories(outFile.getParent());
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(outFile), StandardCharsets.UTF_8));
        try {
            List<String> owners = new ArrayList<String>(classes.keySet());
            Collections.sort(owners);
            for (String owner : owners) {
                ClassNode cn = classes.get(owner);
                List<MethodNode> methods = new ArrayList<MethodNode>(cn.methods);
                Collections.sort(methods, new Comparator<MethodNode>() {
                    public int compare(MethodNode a, MethodNode b) {
                        int c = a.name.compareTo(b.name); if (c!=0) return c;
                        return a.desc.compareTo(b.desc);
                    }
                });
                for (MethodNode mn : methods) {
                    if ((mn.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                    NormalizedMethod nm = new NormalizedMethod(owner, mn, Collections.<Integer>emptySet());
                    writeJsonl(bw, owner, mn.name, mn.desc, nm.extract());
                }
            }
        } finally {
            bw.flush();
            bw.close();
        }
    }

    // Minimal JSON writer with deterministic key order. No escaping beyond common cases.
    private static void writeJsonl(BufferedWriter bw, String owner, String name, String desc,
                                   io.bytecodemapper.signals.normalized.NormalizedFeatures nf) throws IOException {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        // Stable top-level ordering
        field(sb, "owner", owner).append(',');
        field(sb, "name", name).append(',');
        field(sb, "desc", desc).append(',');
        sb.append("\"nsf64\":").append(String.valueOf(nf.nsf64)).append(',');
        // opcodeBag
        sb.append("\"opcodeBag\":"); writeStringIntMap(sb, nf.opcodeBag); sb.append(',');
        // callKinds
        sb.append("\"callKinds\":"); writeStringIntMap(sb, nf.callKinds); sb.append(',');
        // stackDeltaHist
        sb.append("\"stackDeltaHist\":"); writeStringIntMap(sb, nf.stackDeltaHist); sb.append(',');
        // tryShape
        sb.append("\"tryShape\":{");
        sb.append("\"depth\":").append(nf.tryShape.depth).append(',');
        sb.append("\"fanout\":").append(nf.tryShape.fanout).append(',');
        sb.append("\"catchTypeHash\":").append(nf.tryShape.catchTypeHash).append('}').append(',');
        // literalsSketch
        sb.append("\"literalsSketch\":");
        if (nf.literalsSketch != null && nf.literalsSketch.sketch != null) {
            sb.append('[');
            for (int i=0;i<nf.literalsSketch.sketch.length;i++) {
                if (i>0) sb.append(',');
                sb.append(nf.literalsSketch.sketch[i]);
            }
            sb.append(']');
        } else {
            sb.append("null");
        }
        sb.append(',');
        // stringsSketch (tf)
        sb.append("\"stringsTf\":"); writeStringFloatMap(sb, nf.stringsSketch != null ? nf.stringsSketch.tf : null);
        sb.append('}').append('\n');
        bw.write(sb.toString());
    }

    private static StringBuilder field(StringBuilder sb, String key, String val) {
        sb.append('"').append(escapeJson(key)).append('"').append(':');
        sb.append('"').append(escapeJson(val)).append('"');
        return sb;
    }

    private static void writeStringIntMap(StringBuilder sb, Map<String,Integer> m) {
        if (m == null) { sb.append("{}"); return; }
        List<String> keys = new ArrayList<String>(m.keySet());
        Collections.sort(keys);
        sb.append('{');
        for (int i=0;i<keys.size();i++) {
            if (i>0) sb.append(',');
            String k = keys.get(i);
            sb.append('"').append(escapeJson(k)).append('"').append(':').append(String.valueOf(m.get(k)));
        }
        sb.append('}');
    }

    private static void writeStringFloatMap(StringBuilder sb, Map<String,Float> m) {
        if (m == null) { sb.append("{}"); return; }
        List<String> keys = new ArrayList<String>(m.keySet());
        Collections.sort(keys);
        sb.append('{');
        for (int i=0;i<keys.size();i++) {
            if (i>0) sb.append(',');
            String k = keys.get(i);
            sb.append('"').append(escapeJson(k)).append('"').append(':');
            float v = m.get(k) == null ? 0f : m.get(k).floatValue();
            sb.append(String.format(java.util.Locale.ROOT, "%.6f", new Object[]{Float.valueOf(v)}));
        }
        sb.append('}');
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length()+8);
        for (int i=0;i<s.length();i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", new Object[]{Integer.valueOf(c)}));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI NormalizedDumpWriter END
