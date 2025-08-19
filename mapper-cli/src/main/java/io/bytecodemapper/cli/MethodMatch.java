// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.*;
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
import io.bytecodemapper.signals.idf.IdfStore;
// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch use CliPaths BEGIN
// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch use resolveInputOutput BEGIN
import io.bytecodemapper.cli.util.CliPaths;
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch use resolveInputOutput END
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch use CliPaths END
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Phase-2 method matching with composite scoring and abstention. */
final class MethodMatch {

    static void run(String[] args) throws Exception {
        Path oldArg=null, newArg=null, classMapArg=null, outArg=null; boolean doRefine=false;
        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if ("--old".equals(a) && i+1<args.length) oldArg = Paths.get(args[++i]);
            else if ("--new".equals(a) && i+1<args.length) newArg = Paths.get(args[++i]);
            else if ("--classMap".equals(a) && i+1<args.length) classMapArg = Paths.get(args[++i]);
            else if ("--out".equals(a) && i+1<args.length) outArg = Paths.get(args[++i]);
            else if ("--refine".equals(a)) doRefine = true;
            else System.err.println("Unknown or incomplete arg: " + a);
        }
        if (oldArg==null || newArg==null || classMapArg==null || outArg==null) {
            throw new IllegalArgumentException("Usage: methodMatch --old <old.jar> --new <new.jar> --classMap build/classmap.txt --out build/methodmap.txt");
        }

    // >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch use resolveInputOutput BEGIN
    // Resolve paths via shared helper: inputs vs outputs
    Path oldPath = CliPaths.resolveInput(oldArg.toString());
    Path newPath = CliPaths.resolveInput(newArg.toString());
    Path classMapPath = CliPaths.resolveInput(classMapArg.toString());
    Path outPath = CliPaths.resolveOutput(outArg.toString());
    // <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch use resolveInputOutput END
    File oldJar = oldPath.toFile();
    File newJar = newPath.toFile();
    File classMapFile = classMapPath.toFile();
    File outFile = outPath.toFile();

        if (!oldJar.isFile()) throw new FileNotFoundException("old jar not found: " + oldJar);
        if (!newJar.isFile()) throw new FileNotFoundException("new jar not found: " + newJar);
        if (!classMapFile.isFile()) throw new FileNotFoundException("class map not found: " + classMapFile);
        if (outFile.getParentFile()!=null) outFile.getParentFile().mkdirs();

        Map<String,String> classMap = readClassMap(classMapFile.toPath());
        System.out.println("Loaded class map: " + classMap.size() + " pairs");

        // Read classes deterministically
        final List<ClassNode> oldClasses = new ArrayList<ClassNode>();
        final List<ClassNode> newClasses = new ArrayList<ClassNode>();
        ClasspathScanner scanner = new ClasspathScanner();
        scanner.scan(oldJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { oldClasses.add(cn); }});
        scanner.scan(newJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { newClasses.add(cn); }});

        Map<String,ClassNode> oldByName = indexByName(oldClasses);
        Map<String,ClassNode> newByName = indexByName(newClasses);

        MethodFeatureExtractor extractor = new MethodFeatureExtractor();

        // micro IDF from store (empty/new store -> idf=1.0)
        IdfStore microIdf = new IdfStore();

        int matched=0, abstained=0, total=0;

    PrintWriter pw = new PrintWriter(outFile, "UTF-8");
        try {
            // >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch HEADER BEGIN
            pw.println("# BytecodeMapper Method Map");
            pw.println("# S_total = 0.45*calls + 0.25*micro + 0.15*opcode + 0.10*strings + 0.05*fields");
            pw.println("# abstain if (best-second) < 0.05 or best < 0.60");
            // <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch HEADER END
            for (Map.Entry<String,String> e : classMap.entrySet()) {
                String oldOwner = e.getKey();
                String newOwner = e.getValue();
                ClassNode oldCn = oldByName.get(oldOwner);
                ClassNode newCn = newByName.get(newOwner);
                if (oldCn == null || newCn == null) continue;

                // Index methods deterministically (skip abstract/native)
                // >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch use filtered sort BEGIN
                List<MethodNode> oldMethods = sortMethodsFiltered(oldCn);
                List<MethodNode> newMethods = sortMethodsFiltered(newCn);
                // <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch use filtered sort END

                // Extract features
                Map<MethodRef,MethodFeatures> oldFeats = new LinkedHashMap<MethodRef,MethodFeatures>();
                Map<MethodRef,MethodFeatures> newFeats = new LinkedHashMap<MethodRef,MethodFeatures>();

                final Map<String,String> classMapFinal = classMap; // capture
                MethodFeatureExtractor.ClassOwnerMapper mapper = new MethodFeatureExtractor.ClassOwnerMapper() {
                    public String mapOldOwnerToNew(String o) {
                        String m = classMapFinal.get(o);
                        return m != null ? m : o;
                    }
                };

                for (MethodNode mn : oldMethods) {
                    MethodFeatures f = extractor.extractForOld(oldCn, mn, mapper);
                    oldFeats.put(f.ref, f);
                }
                for (MethodNode mn : newMethods) {
                    MethodFeatures f = extractor.extractForNew(newCn, mn);
                    newFeats.put(f.ref, f);
                }

                // Build candidate index (same target class)
                List<MethodFeatures> targetList = new ArrayList<MethodFeatures>(newFeats.values());

                // >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch refine block BEGIN
                if (doRefine) {
                    // Build per-source candidate sets and base scores
                    Map<MethodRef, io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet> candMap = new LinkedHashMap<MethodRef, io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet>();
                    for (MethodFeatures src : oldFeats.values()) {
                        List<MethodFeatures> cands = CandidateGenerator.topKByWl(src, targetList, CandidateGenerator.DEFAULT_TOPK);
                        double[] base = MethodScorer.scoreVector(src, cands, microIdf);
                        candMap.put(src.ref, new io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet(cands, base));
                    }

                    // Intra-class graphs
                    Map<MethodRef, java.util.Set<MethodRef>> gOld = io.bytecodemapper.cli.method.AppCallGraph.buildIntraClassGraph(oldCn);
                    Map<MethodRef, java.util.Set<MethodRef>> gNew = io.bytecodemapper.cli.method.AppCallGraph.buildIntraClassGraph(newCn);

                    io.bytecodemapper.cli.method.CallGraphRefiner.Result rr = io.bytecodemapper.cli.method.CallGraphRefiner.refine(candMap, gOld, gNew,
                            io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_LAMBDA, io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_MAX_ITERS);

                    // Emit refined results deterministically by source name/desc
                    List<MethodRef> srcOrder = new ArrayList<MethodRef>(rr.mapping.keySet());
                    Collections.sort(srcOrder, new Comparator<MethodRef>() {
                        public int compare(MethodRef a, MethodRef b) {
                            int c = a.name.compareTo(b.name); return c!=0?c:a.desc.compareTo(b.desc);
                        }
                    });
                    for (MethodRef u : srcOrder) {
                        MethodRef v = rr.mapping.get(u);
                        Double s = rr.bestScore.get(u);
                        total++;
                        if (v != null && s != null && s.doubleValue() >= io.bytecodemapper.cli.method.MethodScorer.TAU_ACCEPT) {
                            matched++;
                            pw.println(u.toString() + " -> " + v.toString() + " score=" + String.format(java.util.Locale.ROOT, "%.4f", s.doubleValue()) + " [refined]");
                        } else {
                            abstained++;
                            pw.println("# abstain refine | " + u.toString() + (s!=null? (" score=" + String.format(java.util.Locale.ROOT, "%.4f", s.doubleValue())) : ""));
                        }
                    }
                    // brief stats
                    pw.println(String.format(java.util.Locale.ROOT, "# refine stats iters=%d lastFlips=%d lastMaxDelta=%.5f",
                            rr.stats.iters, rr.stats.flipsPerIter[rr.stats.flipsPerIter.length-1], rr.stats.maxDeltaPerIter[rr.stats.maxDeltaPerIter.length-1]));

                } else {
                    for (MethodFeatures src : oldFeats.values()) {
                        total++;
                        List<MethodFeatures> cands = CandidateGenerator.topKByWl(src, targetList, CandidateGenerator.DEFAULT_TOPK);
                        MethodScorer.Result r = MethodScorer.scoreOne(src, cands, microIdf);

                        if (r.accepted && r.best != null) {
                            matched++;
                            pw.println(src.ref.toString() + " -> " + r.best.ref.toString() +
                                    " score=" + String.format(java.util.Locale.ROOT, "%.4f", r.scoreBest));
                        } else {
                            abstained++;
                            // For audit, write top candidate if present
                            if (r.best != null) {
                                pw.println("# abstain " + r.abstainReason + " | " + src.ref.toString() + " -> " + r.best.ref.toString() +
                                        " score=" + String.format(java.util.Locale.ROOT, "%.4f", r.scoreBest) +
                                        " second=" + String.format(java.util.Locale.ROOT, "%.4f", r.scoreSecond));
                            } else {
                                pw.println("# abstain " + r.abstainReason + " | " + src.ref.toString());
                            }
                        }
                    }
                }
                // <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch refine block END
            }
        } finally {
            pw.close();
        }
        System.out.println("Method matching complete. matched=" + matched + " abstained=" + abstained + " total=" + total);
        System.out.println("Wrote method map: " + outFile.getAbsolutePath());
    }

    private static Map<String,String> readClassMap(Path p) throws IOException {
        Map<String,String> m = new LinkedHashMap<String,String>();
        List<String> lines = Files.readAllLines(p);
        for (String s : lines) {
            s = s.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            // expected: old -> new score=...
            int arrow = s.indexOf("->");
            if (arrow <= 0) continue;
            String left = s.substring(0, arrow).trim();
            String right = s.substring(arrow+2).trim();
            int sp = right.indexOf(' ');
            if (sp >= 0) right = right.substring(0, sp).trim();
            m.put(left, right);
        }
        return m;
    }

    private static Map<String,ClassNode> indexByName(List<ClassNode> list) {
        Map<String,ClassNode> m = new HashMap<String,ClassNode>(list.size()*2);
        for (ClassNode cn : list) m.put(cn.name, cn);
        return m;
    }



    // >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch skip abstract/native BEGIN
    private static java.util.List<org.objectweb.asm.tree.MethodNode> sortMethodsFiltered(org.objectweb.asm.tree.ClassNode cn) {
        java.util.List<org.objectweb.asm.tree.MethodNode> ms = new java.util.ArrayList<org.objectweb.asm.tree.MethodNode>(cn.methods);
        java.util.Iterator<org.objectweb.asm.tree.MethodNode> it = ms.iterator();
        while (it.hasNext()) {
            org.objectweb.asm.tree.MethodNode m = it.next();
            int acc = m.access;
            boolean isAbstract = (acc & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0;
            boolean isNative   = (acc & org.objectweb.asm.Opcodes.ACC_NATIVE) != 0;
            if (isAbstract || isNative) it.remove();
        }
        java.util.Collections.sort(ms, new java.util.Comparator<org.objectweb.asm.tree.MethodNode>() {
            public int compare(org.objectweb.asm.tree.MethodNode a, org.objectweb.asm.tree.MethodNode b) {
                int c = a.name.compareTo(b.name);
                return c != 0 ? c : a.desc.compareTo(b.desc);
            }
        });
        return ms;
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch skip abstract/native END

    private MethodMatch(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch END
