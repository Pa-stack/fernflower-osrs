// >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.MethodFeatureExtractor;
import io.bytecodemapper.cli.method.MethodFeatures;
import io.bytecodemapper.cli.method.MethodRef;
import io.bytecodemapper.cli.util.CliPaths;
import io.bytecodemapper.cli.util.DebugNormalizedDump;
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
// >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 BEGIN
import io.bytecodemapper.io.tiny.TinyV2Writer;
// <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 END
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
        boolean debugStats = false;

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
            } else if ("--debug-stats".equals(a)) {
                debugStats = true;
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

        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 BEGIN
        // Build TinyV2 inputs from computed maps
        // Expect these maps to exist after phases:
        //   classMap: Map<String,String>  (obfOwner -> deobfOwner)
        //   methodPairs: Map<io.bytecodemapper.cli.method.MethodRef, io.bytecodemapper.cli.method.MethodRef>
        //   fieldPairs:  Map<io.bytecodemapper.cli.field.FieldRef,  io.bytecodemapper.cli.field.FieldRef>
        // If your variables differ, adapt accordingly.

        // Placeholders for now; pipeline phases should populate these before this point.
        Map<String,String> classMap = new LinkedHashMap<String,String>();
        Map<io.bytecodemapper.cli.method.MethodRef, io.bytecodemapper.cli.method.MethodRef> methodPairs = new LinkedHashMap<io.bytecodemapper.cli.method.MethodRef, io.bytecodemapper.cli.method.MethodRef>();
        Map<io.bytecodemapper.cli.field.FieldRef,  io.bytecodemapper.cli.field.FieldRef>  fieldPairs  = new LinkedHashMap<io.bytecodemapper.cli.field.FieldRef,  io.bytecodemapper.cli.field.FieldRef>();

        // >>> AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG STATS BEGIN
        if (debugStats) {
            System.out.println("[Phase-1] Classes: old=" + oldClasses.size() + " new=" + newClasses.size()
                    + " matched=" + classMap.size() + " abstained=" + (oldClasses.size() - classMap.size()));
        }

        if (debugStats) {
            int accepted = methodPairs.size();
            int abstained = Math.max(0, oldMf.size() - accepted); // best-effort approximation
            int leafPenalty = 0; // optional counters not tracked yet
            int recPenalty  = 0;
            int lowScore    = 0;
            int marginFail  = 0;
            System.out.println("[Phase-2] Methods: accepted=" + accepted + " abstained=" + abstained
                    + " lowScore=" + lowScore + " marginFail=" + marginFail
                    + " leafPenalty=" + leafPenalty + " recPenalty=" + recPenalty);
        }

        // If refinement is implemented later, these will be replaced.
        boolean refined = false; int flipsLastIter = 0; double maxDelta = 0.0;
        if (debugStats && refined) {
            System.out.println("[Phase-3] Refinement: flipsLastIter=" + flipsLastIter
                    + " maxDelta=" + String.format(java.util.Locale.ROOT, "%.4f", maxDelta)
                    + " accepted=" + methodPairs.size());
        }

        if (debugStats && fieldPairs != null) {
            System.out.println("[Phase-4] Fields: accepted=" + fieldPairs.size());
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew DEBUG STATS END

        Map<String,String> tinyClasses = new java.util.LinkedHashMap<String,String>(classMap);

        java.util.List<TinyV2Writer.MethodEntry> tinyMethods = new java.util.ArrayList<TinyV2Writer.MethodEntry>();
        for (java.util.Map.Entry<io.bytecodemapper.cli.method.MethodRef, io.bytecodemapper.cli.method.MethodRef> e : methodPairs.entrySet()) {
            io.bytecodemapper.cli.method.MethodRef obf = e.getKey();
            io.bytecodemapper.cli.method.MethodRef neo = e.getValue();
            tinyMethods.add(new TinyV2Writer.MethodEntry(obf.owner, obf.name, obf.desc, neo.name));
        }

        java.util.List<TinyV2Writer.FieldEntry> tinyFields = new java.util.ArrayList<TinyV2Writer.FieldEntry>();
        if (fieldPairs != null) {
            for (java.util.Map.Entry<io.bytecodemapper.cli.field.FieldRef, io.bytecodemapper.cli.field.FieldRef> e : fieldPairs.entrySet()) {
                io.bytecodemapper.cli.field.FieldRef obf = e.getKey();
                io.bytecodemapper.cli.field.FieldRef neo = e.getValue();
                tinyFields.add(new TinyV2Writer.FieldEntry(obf.owner, obf.name, obf.desc, neo.name));
            }
        }

        // Write tiny v2
        java.nio.file.Path outTiny = outPath; // already resolved output path
        io.bytecodemapper.io.tiny.TinyV2Writer.writeTiny2(outTiny, tinyClasses, tinyMethods, tinyFields);
        System.out.println("Wrote Tiny v2 to: " + outTiny);
        // <<< AUTOGEN: BYTECODEMAPPER CLI MapOldNew WRITE TINYV2 END
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
