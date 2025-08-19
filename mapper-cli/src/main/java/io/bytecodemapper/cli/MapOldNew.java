// >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.MethodFeatureExtractor;
import io.bytecodemapper.cli.method.MethodFeatures;
import io.bytecodemapper.cli.method.MethodRef;
import io.bytecodemapper.cli.util.CliPaths;
import io.bytecodemapper.cli.util.DebugNormalizedDump;
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
import io.bytecodemapper.io.TinyLikeWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.*;

final class MapOldNew {

    static void run(String[] args) throws Exception {
        File oldJar = null, newJar = null; File out = null;
        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if ("--old".equals(a) && i+1<args.length) oldJar = new File(args[++i]);
            else if ("--new".equals(a) && i+1<args.length) newJar = new File(args[++i]);
            else if ("--out".equals(a) && i+1<args.length) out = new File(args[++i]);
        }
        if (oldJar == null || newJar == null || out == null) {
            throw new IllegalArgumentException("Usage: mapOldNew --old <old.jar> --new <new.jar> --out <out.tiny>");
        }

        // Resolve IO
        Path oldPath = CliPaths.resolveInput(oldJar.getPath());
        Path newPath = CliPaths.resolveInput(newJar.getPath());
        Path outPath = CliPaths.resolveOutput(out.getPath());
        oldJar = oldPath.toFile(); newJar = newPath.toFile(); out = outPath.toFile();
        if (!oldJar.isFile()) throw new FileNotFoundException("old jar not found: " + oldJar);
        if (!newJar.isFile()) throw new FileNotFoundException("new jar not found: " + newJar);
        if (out.getParentFile()!=null) out.getParentFile().mkdirs();

        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG FLAGS SCOPE BEGIN
        // Parse debug flags only for mapOldNew
        boolean debugNormalized = false;
        String debugNormalizedPath = null;
        int debugSample = 50;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--debug-normalized".equals(a)) {
                debugNormalized = true;
                // optional path value next
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    debugNormalizedPath = args[++i];
                }
            } else if ("--debug-sample".equals(a)) {
                if (i + 1 < args.length) {
                    try { debugSample = Integer.parseInt(args[++i]); } catch (NumberFormatException ignore) {}
                }
            }
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG FLAGS SCOPE END

        // Read classes deterministically
        final List<ClassNode> oldClasses = new ArrayList<ClassNode>();
        final List<ClassNode> newClasses = new ArrayList<ClassNode>();
        ClasspathScanner scanner = new ClasspathScanner();
        scanner.scan(oldJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { oldClasses.add(cn); }});
        scanner.scan(newJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { newClasses.add(cn); }});

        // Build method features across both sets (deterministic order; skip abstract/native)
        Map<MethodRef, MethodFeatures> oldMf = new LinkedHashMap<MethodRef, MethodFeatures>();
        Map<MethodRef, MethodFeatures> newMf = new LinkedHashMap<MethodRef, MethodFeatures>();
        MethodFeatureExtractor extractor = new MethodFeatureExtractor();

        for (ClassNode cn : oldClasses) {
            for (MethodNode mn : sortMethodsFiltered(cn)) {
                MethodFeatures f = extractor.extractForOld(cn, mn, new MethodFeatureExtractor.ClassOwnerMapper() {
                    public String mapOldOwnerToNew(String o) { return o; }
                });
                oldMf.put(f.ref, f);
            }
        }
        for (ClassNode cn : newClasses) {
            for (MethodNode mn : sortMethodsFiltered(cn)) {
                MethodFeatures f = extractor.extractForNew(cn, mn);
                newMf.put(f.ref, f);
            }
        }

        // Debug dump (scoped to this command)
        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG DUMP CALL BEGIN
    if (debugNormalized) {
        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG PATH RESOLUTION BEGIN
        java.nio.file.Path dbgOutPath = io.bytecodemapper.cli.util.CliPaths.resolveOutput(
            debugNormalizedPath != null ? debugNormalizedPath : "mapper-cli/build/normalized_debug.txt");
        java.nio.file.Files.createDirectories(dbgOutPath.getParent());
        io.bytecodemapper.cli.util.DebugNormalizedDump.writeSample(
            oldJar, newJar, dbgOutPath, debugSample, oldMf, newMf);
        System.out.println("Wrote normalized debug to: " + dbgOutPath);
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG PATH RESOLUTION END
    }
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG DUMP CALL END

        // For now, emit a tiny-like stub mapping (deterministic dummy line)
        List<String[]> pairs = new ArrayList<String[]>();
        pairs.add(new String[]{"a/A", "deob/A"});
        TinyLikeWriter.writeClasses(out, pairs);
        System.out.println("Wrote mappings: " + out.getAbsolutePath());
    }

    // Deterministic filtered method list (skip abstract/native)
    private static List<MethodNode> sortMethodsFiltered(ClassNode cn) {
        List<MethodNode> ms = new ArrayList<MethodNode>(cn.methods);
        Iterator<MethodNode> it = ms.iterator();
        while (it.hasNext()) {
            MethodNode m = it.next();
            int acc = m.access;
            boolean isAbstract = (acc & Opcodes.ACC_ABSTRACT) != 0;
            boolean isNative   = (acc & Opcodes.ACC_NATIVE) != 0;
            if (isAbstract || isNative) it.remove();
        }
        Collections.sort(ms, new Comparator<MethodNode>() {
            public int compare(MethodNode a, MethodNode b) {
                int c = a.name.compareTo(b.name);
                return c != 0 ? c : a.desc.compareTo(b.desc);
            }
        });
        return ms;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew END
