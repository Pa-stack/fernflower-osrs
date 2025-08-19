// >>> AUTOGEN: BYTECODEMAPPER CLI Main BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.io.TinyLikeWriter;
// >>> AUTOGEN: BYTECODEMAPPER CLI Main mapOldNew DEBUG imports BEGIN
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
import io.bytecodemapper.core.normalize.Normalizer;
import io.bytecodemapper.signals.normalized.NormalizedAdapters;
import io.bytecodemapper.signals.normalized.NormalizedMethod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
// <<< AUTOGEN: BYTECODEMAPPER CLI Main mapOldNew DEBUG imports END

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintWriter;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0])) {
            System.out.println("Commands:");
            System.out.println("  mapOldNew --old <old.jar> --new <new.jar> --out <out.tiny>");
            System.out.println("             [--debug-normalized [path]] [--debug-sample N]");
            System.out.println("  applyMappings --inJar <in.jar> --mappings <mappings.tiny> --out <out.jar>");
            System.out.println("  printIdf --out <path> [--from <existing.properties>] [--lambda 0.9]");
            return;
        }
        if ("printIdf".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            PrintIdf.run(tail);
            return;
        }
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main classMatch DISPATCH BEGIN
        if ("classMatch".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            ClassMatch.run(tail);
            return;
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main classMatch DISPATCH END
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main methodMatch DISPATCH BEGIN
        if ("methodMatch".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            MethodMatch.run(tail);
            return;
        }
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main fieldMatch DISPATCH BEGIN
        if ("fieldMatch".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            FieldMatch.run(tail);
            return;
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main fieldMatch DISPATCH END
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main methodMatch DISPATCH END
        if ("mapOldNew".equals(args[0])) {
            File out = new File(findArg(args, "--out", "build/mappings.tiny"));
            // >>> AUTOGEN: BYTECODEMAPPER CLI Main mapOldNew DEBUG BEGIN
            boolean debugNorm = hasFlag(args, "--debug-normalized");
            String debugPath = findArgOptional(args, "--debug-normalized");
            int sample = parseIntOr(args, "--debug-sample", 50);

            File oldJar = new File(findArg(args, "--old", null));
            File newJar = new File(findArg(args, "--new", null));
            if (debugNorm) {
                File dbgOut = new File(debugPath != null ? debugPath : "mapper-cli/build/normalized_debug.txt");
                if (dbgOut.getParentFile() != null) dbgOut.getParentFile().mkdirs();
                try {
                    dumpNormalizedSample(oldJar, newJar, dbgOut, sample);
                    System.out.println("Debug-normalized dump: " + dbgOut.getAbsolutePath());
                } catch (Exception e) {
                    System.err.println("debug-normalized failed: " + e.getMessage());
                }
            }
            // <<< AUTOGEN: BYTECODEMAPPER CLI Main mapOldNew DEBUG END

            // Placeholder: write a dummy class mapping to prove pipeline works.
            List<String[]> pairs = new ArrayList<String[]>();
            pairs.add(new String[]{"a/A", "deob/A"});
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            TinyLikeWriter.writeClasses(out, pairs);
            System.out.println("Wrote mappings: " + out.getAbsolutePath());
            return;
        }
        if ("applyMappings".equals(args[0])) {
            File in = new File(findArg(args, "--inJar", null));
            File map = new File(findArg(args, "--mappings", null));
            File out = new File(findArg(args, "--out", "build/new-mapped.jar"));
            if (in == null || map == null) {
                System.err.println("Missing --inJar or --mappings");
                System.exit(2);
            }
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            // Stub: copy input to output unchanged.
            java.nio.file.Files.copy(in.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Stub-applied mappings (no-op): " + out.getAbsolutePath());
            return;
        }
        System.err.println("Unknown command: " + args[0]);
        System.exit(2);
    }

    private static String findArg(String[] a, String key, String def) {
        for (int i=0;i<a.length-1;i++) if (key.equals(a[i])) return a[i+1];
        return def;
    }

    // >>> AUTOGEN: BYTECODEMAPPER CLI Main mapOldNew DEBUG helpers BEGIN
    private static boolean hasFlag(String[] a, String key) {
        for (int i=0;i<a.length;i++) if (key.equals(a[i])) return true;
        return false;
    }
    private static String findArgOptional(String[] a, String key) {
        for (int i=0;i<a.length-1;i++) if (key.equals(a[i])) return a[i+1];
        return null;
    }
    private static int parseIntOr(String[] a, String key, int def) {
        for (int i=0;i<a.length-1;i++) if (key.equals(a[i])) {
            try { return Integer.parseInt(a[i+1]); } catch (Exception ignored) { return def; }
        }
        return def;
    }

    /** Deterministically dump a compact sample of normalized features from both jars. */
    private static void dumpNormalizedSample(File oldJar, File newJar, File out, int maxCount) throws Exception {
        if (oldJar == null || newJar == null) throw new IllegalArgumentException("--old/--new required for --debug-normalized");

        List<ClassNode> oldClasses = new ArrayList<ClassNode>();
        List<ClassNode> newClasses = new ArrayList<ClassNode>();
        ClasspathScanner scanner = new ClasspathScanner();
        scanner.scan(oldJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { oldClasses.add(cn); }});
        scanner.scan(newJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { newClasses.add(cn); }});

        PrintWriter pw = new PrintWriter(out, "UTF-8");
        try {
            pw.println("# Normalized features sample (deterministic order)");
            int wrote = 0;
            wrote = dumpFromSet(pw, "OLD", oldClasses, maxCount, 0);
            if (wrote < maxCount) dumpFromSet(pw, "NEW", newClasses, maxCount, wrote);
        } finally {
            pw.close();
        }
    }

    private static int dumpFromSet(PrintWriter pw, String tag, List<ClassNode> classes, int maxCount, int already) {
        int wrote = already;
        for (int ci=0; ci<classes.size() && wrote < maxCount; ci++) {
            ClassNode cn = classes.get(ci);
            List<MethodNode> methods = sortMethodsFiltered(cn);
            for (int mi=0; mi<methods.size() && wrote < maxCount; mi++) {
                MethodNode mn = methods.get(mi);
                // Normalize then build NormalizedMethod features
                Normalizer.Result norm = Normalizer.normalize(mn, Normalizer.Options.defaults());
                NormalizedMethod nm = new NormalizedMethod(cn.name, norm.method, java.util.Collections.<Integer>emptySet());
                int[] hist = NormalizedAdapters.toDense200(nm.opcodeHistogram);

                pw.println(tag + " " + cn.name + "#" + mn.name + mn.desc);
                pw.println("  desc_norm=" + nm.normalizedDescriptor);
                pw.println("  fingerprint=" + nm.fingerprint);
                pw.println("  opcodes_nz=" + summarizeNonZero(hist));
                if (!nm.stringConstants.isEmpty()) pw.println("  strings=" + joinList(nm.stringConstants));
                if (!nm.invokedSignatures.isEmpty()) pw.println("  invoked=" + joinList(nm.invokedSignatures));
                wrote++;
            }
        }
        return wrote;
    }

    private static String summarizeNonZero(int[] hist) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i=0;i<hist.length;i++) {
            if (hist[i] != 0) {
                if (shown > 0) sb.append(',');
                sb.append(i).append(':').append(hist[i]);
                shown++;
                if (shown >= 32) { sb.append(",+"); break; }
            }
        }
        if (shown == 0) return "-";
        return sb.toString();
    }

    private static String joinList(java.util.Collection<String> xs) {
        java.util.List<String> list = new java.util.ArrayList<String>(xs);
        java.util.Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<list.size();i++) { if (i>0) sb.append(','); sb.append(list.get(i)); }
        return sb.toString();
    }

    // Copy of filter/sort util: skip abstract/native methods; deterministic order
    private static List<MethodNode> sortMethodsFiltered(ClassNode cn) {
        List<MethodNode> ms = new ArrayList<MethodNode>(cn.methods);
        java.util.Iterator<MethodNode> it = ms.iterator();
        while (it.hasNext()) {
            MethodNode m = it.next();
            int acc = m.access;
            boolean isAbstract = (acc & Opcodes.ACC_ABSTRACT) != 0;
            boolean isNative   = (acc & Opcodes.ACC_NATIVE) != 0;
            if (isAbstract || isNative) it.remove();
        }
        java.util.Collections.sort(ms, new java.util.Comparator<MethodNode>() {
            public int compare(MethodNode a, MethodNode b) {
                int c = a.name.compareTo(b.name);
                return c != 0 ? c : a.desc.compareTo(b.desc);
            }
        });
        return ms;
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI Main mapOldNew DEBUG helpers END
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Main END
