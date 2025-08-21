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
                    writeJsonl(bw, owner, mn.name, mn.desc, nm, nm.extract());
                }
            }
        } finally {
            bw.flush();
            bw.close();
        }
    }

    // Minimal JSON writer with deterministic key order for NSFv2 dump.
    private static void writeJsonl(BufferedWriter bw, String owner, String name, String desc,
                                   NormalizedMethod nm,
                                   io.bytecodemapper.signals.normalized.NormalizedFeatures nf) throws IOException {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        // Stable top-level ordering (NSFv2 canonical)
        field(sb, "owner", owner).append(',');
        field(sb, "name", name).append(',');
        field(sb, "desc", desc).append(',');
        field(sb, "nsf_version", "NSFv2").append(',');
        field(sb, "nsf64_hex", toHex16(nf.getNsf64())).append(',');
        // stackHist in fixed order
        sb.append("\"stackHist\":{");
        {
            java.util.Map<String,Integer> sh = nf.getStackHist();
            String[] order = new String[]{"-2","-1","0","+1","+2"};
            for (int i=0;i<order.length;i++) {
                if (i>0) sb.append(',');
                String k = order[i];
                sb.append('"').append(escapeJson(k)).append('"').append(':').append(String.valueOf(sh.get(k)));
            }
        }
        sb.append('}').append(',');
        // try shape scalars
        sb.append("\"tryDepth\":").append(nf.getTryDepth()).append(',');
        sb.append("\"tryFanout\":").append(nf.getTryFanout()).append(',');
        sb.append("\"catchTypesHash\":").append(nf.getCatchTypesHash()).append(',');
        // literals minhash 64 or "∅"
        sb.append("\"litsMinHash64\":");
        int[] sk = nf.getLitsMinHash64();
        if (sk == null) {
            sb.append('"').append("∅").append('"');
        } else {
            sb.append('[');
            for (int i=0;i<sk.length;i++) { if (i>0) sb.append(','); sb.append(sk[i]); }
            sb.append(']');
        }
        sb.append(',');
        // invoke kind counts
        sb.append("\"invokeKindCounts\":");
        int[] kc = nf.getInvokeKindCounts();
        sb.append('[').append(kc[0]).append(',').append(kc[1]).append(',').append(kc[2]).append(',').append(kc[3]).append(']').append(',');
        // strings sorted
        java.util.List<String> strings = new java.util.ArrayList<String>(nm.stringConstants);
        java.util.Collections.sort(strings);
        sb.append("\"strings\":"); writeStringArray(sb, strings); sb.append(',');
        // invokes sorted
        java.util.List<String> invs = new java.util.ArrayList<String>(nm.invokedSignatures);
        java.util.Collections.sort(invs);
        sb.append("\"invokes\":"); writeStringArray(sb, invs);
        sb.append('}').append('\n');
        bw.write(sb.toString());
    }

    private static StringBuilder field(StringBuilder sb, String key, String val) {
        sb.append('"').append(escapeJson(key)).append('"').append(':');
        sb.append('"').append(escapeJson(val)).append('"');
        return sb;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    private static void writeStringArray(StringBuilder sb, java.util.List<String> vals) {
        sb.append('[');
        for (int i=0;i<vals.size();i++) { if (i>0) sb.append(','); sb.append('"').append(escapeJson(vals.get(i))).append('"'); }
        sb.append(']');
    }

    private static String toHex16(long v) {
        String s = Long.toHexString(v);
        if (s.length() < 16) {
            StringBuilder p = new StringBuilder(16);
            for (int i=0;i<16-s.length();i++) p.append('0');
            p.append(s);
            return p.toString();
        }
        if (s.length() > 16) return s.substring(s.length()-16);
        return s;
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
