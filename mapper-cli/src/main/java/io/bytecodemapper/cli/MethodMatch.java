// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.*;
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
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
    Path oldArg=null, newArg=null, classMapArg=null, outArg=null;
// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch REFINE FLAGS BEGIN
    boolean refine = false;
    Double lambdaArg = null;
    Integer refineIters = null;
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch REFINE FLAGS END
        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if ("--old".equals(a) && i+1<args.length) oldArg = Paths.get(args[++i]);
            else if ("--new".equals(a) && i+1<args.length) newArg = Paths.get(args[++i]);
            else if ("--classMap".equals(a) && i+1<args.length) classMapArg = Paths.get(args[++i]);
            else if ("--out".equals(a) && i+1<args.length) outArg = Paths.get(args[++i]);
// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch REFINE PARSE BEGIN
        else if ("--refine".equals(a)) { refine = true; }
        else if ("--lambda".equals(a) && i+1<args.length) { lambdaArg = Double.valueOf(args[++i]); }
        else if ("--refineIters".equals(a) && i+1<args.length) { refineIters = Integer.valueOf(args[++i]); }
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch REFINE PARSE END
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
// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch REFINE ANNOUNCE BEGIN
    if (refine) {
        System.out.println("Call-graph refinement ON: lambda=" +
            String.format(java.util.Locale.ROOT, "%.2f", (lambdaArg==null? io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_LAMBDA: lambdaArg.doubleValue())) +
            " maxIters=" + (refineIters==null? io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_MAX_ITERS: refineIters.intValue()));
    } else {
        System.out.println("Call-graph refinement OFF");
    }
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch REFINE ANNOUNCE END

// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch LAMBDA ANNOUNCE BEGIN
        if (refine) {
            double lShow = (lambdaArg==null? io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_LAMBDA : lambdaArg.doubleValue());
            boolean clamped = (lShow < io.bytecodemapper.cli.method.CallGraphRefiner.LAMBDA_MIN) || (lShow > io.bytecodemapper.cli.method.CallGraphRefiner.LAMBDA_MAX);
            System.out.println("Call-graph refinement ON: lambda=" +
                    String.format(java.util.Locale.ROOT, "%.2f", lShow) +
                    (clamped ? " (will be clamped to policy range 0.60–0.80)" : "") +
                    " maxIters=" + (refineIters==null? io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_MAX_ITERS: refineIters.intValue()));
        }
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch LAMBDA ANNOUNCE END

        // Read classes deterministically
        final List<ClassNode> oldClasses = new ArrayList<ClassNode>();
        final List<ClassNode> newClasses = new ArrayList<ClassNode>();
        ClasspathScanner scanner = new ClasspathScanner();
        scanner.scan(oldJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { oldClasses.add(cn); }});
        scanner.scan(newJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { newClasses.add(cn); }});

        Map<String,ClassNode> oldByName = indexByName(oldClasses);
        Map<String,ClassNode> newByName = indexByName(newClasses);

        MethodFeatureExtractor extractor = new MethodFeatureExtractor();

    // micro IDF is built per-src where needed in refine block

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

                // >>> AUTOGEN: BYTECODEMAPPER CLI MethodMatch PER CLASS REFINE BLOCK BEGIN
                // Candidate sets and base scores
                Map<MethodRef, io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet> candidateSets = new LinkedHashMap<MethodRef, io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet>();
                java.util.List<MethodFeatures> targetList = new java.util.ArrayList<MethodFeatures>(newFeats.values());

                for (MethodFeatures src : oldFeats.values()) {
                    java.util.List<MethodFeatures> cands = io.bytecodemapper.cli.method.CandidateGenerator.topKByWl(src, targetList, io.bytecodemapper.cli.method.CandidateGenerator.DEFAULT_TOPK);

                    // Build models (calls/strings) once per src for determinism
                    java.util.List<java.util.List<String>> callDocs = new java.util.ArrayList<java.util.List<String>>(cands.size()+1);
                    callDocs.add(src.callBagNormalized);
                    for (MethodFeatures m : cands) callDocs.add(m.callBagNormalized);
                    io.bytecodemapper.signals.tfidf.TfIdfModel callsModel = io.bytecodemapper.signals.calls.CallBagTfidf.buildModel(callDocs);

                    java.util.List<java.util.List<String>> strDocs = new java.util.ArrayList<java.util.List<String>>(cands.size()+1);
                    strDocs.add(src.stringBag);
                    for (MethodFeatures m : cands) strDocs.add(m.stringBag);
                    io.bytecodemapper.signals.tfidf.TfIdfModel strModel = io.bytecodemapper.signals.strings.StringTfidf.buildModel(strDocs);

                    io.bytecodemapper.signals.micro.MicroScoringService microSvc =
                            new io.bytecodemapper.signals.micro.MicroScoringService().setIdf(new io.bytecodemapper.signals.idf.IdfStore().computeIdf());

                    double[] baseScores = new double[cands.size()];
                    for (int i=0;i<cands.size();i++) {
                        MethodFeatures t = cands.get(i);
                        double sCalls = io.bytecodemapper.signals.calls.CallBagTfidf.cosineSimilarity(callsModel, src.callBagNormalized, t.callBagNormalized);
                        double sMicro = microSvc.similarity(src.microBits, t.microBits, io.bytecodemapper.cli.method.MethodScorer.ALPHA_MP);
                        double sOpc   = io.bytecodemapper.signals.opcode.OpcodeFeatures.cosineHistogram(src.opcodeHistogram, t.opcodeHistogram);
                        double sStr   = io.bytecodemapper.signals.strings.StringTfidf.cosineSimilarity(strModel, src.stringBag, t.stringBag);
                        double s = io.bytecodemapper.cli.method.MethodScorer.W_CALLS*sCalls
                                + io.bytecodemapper.cli.method.MethodScorer.W_MICRO*sMicro
                                + io.bytecodemapper.cli.method.MethodScorer.W_OPCODE*sOpc
                                + io.bytecodemapper.cli.method.MethodScorer.W_STR*sStr;

                        if (src.leaf != t.leaf) s -= io.bytecodemapper.cli.method.MethodScorer.PEN_LEAF_MISMATCH;
                        if (src.recursive != t.recursive) s -= io.bytecodemapper.cli.method.MethodScorer.PEN_RECUR_MISMATCH;

                        if (s < 0) s = 0; if (s > 1) s = 1;
                        baseScores[i] = s;
                    }
                    candidateSets.put(src.ref, new io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet(cands, baseScores));
                }

                java.util.Map<MethodRef, MethodRef> finalMapForPair;
                java.util.Map<MethodRef, Double>    finalScoreForPair;

                if (refine) {
                    double lambda = (lambdaArg!=null? lambdaArg.doubleValue(): io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_LAMBDA);
                    int maxIters  = (refineIters!=null? refineIters.intValue(): io.bytecodemapper.cli.method.CallGraphRefiner.DEFAULT_MAX_ITERS);

                    java.util.Map<MethodRef, java.util.Set<MethodRef>> adjOld = io.bytecodemapper.cli.method.AppCallGraph.buildIntraClassGraph(oldCn);
                    java.util.Map<MethodRef, java.util.Set<MethodRef>> adjNew = io.bytecodemapper.cli.method.AppCallGraph.buildIntraClassGraph(newCn);

                    io.bytecodemapper.cli.method.CallGraphRefiner.Result rr =
                            io.bytecodemapper.cli.method.CallGraphRefiner.refine(candidateSets, adjOld, adjNew, lambda, maxIters);

                    finalMapForPair   = rr.mapping;
                    finalScoreForPair = rr.bestScore;

                    // Print oscillation metric trend
                    System.out.print("Refine stats: flips=");
                    for (int i=0;i<rr.stats.flipsPerIter.length;i++) {
                        System.out.print((i==0?"":"->") + rr.stats.flipsPerIter[i]);
                    }
                    System.out.println();
                } else {
                    finalMapForPair   = new java.util.LinkedHashMap<MethodRef, MethodRef>();
                    finalScoreForPair = new java.util.LinkedHashMap<MethodRef, Double>();
                    for (java.util.Map.Entry<MethodRef, io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet> e2 : candidateSets.entrySet()) {
                        MethodRef u = e2.getKey();
                        io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet cs = e2.getValue();
                        int bi = 0; double best = -1.0;
                        for (int i=0;i<cs.baseScores.length;i++) if (cs.baseScores[i] > best) { best = cs.baseScores[i]; bi = i; }
                        finalMapForPair.put(u, cs.targets.get(bi).ref);
                        finalScoreForPair.put(u, Double.valueOf(best));
                    }
                }

                // Emit with abstention using refined(best) and observed second best (from base set)
                for (java.util.Map.Entry<MethodRef, io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet> e2 : candidateSets.entrySet()) {
                    MethodRef u = e2.getKey();
                    io.bytecodemapper.cli.method.CallGraphRefiner.CandidateSet cs = e2.getValue();

                    MethodRef vStar = finalMapForPair.get(u);
                    if (vStar == null) { abstained++; pw.println("# abstain no_best | " + u); total++; continue; }

                    double best = finalScoreForPair.get(u).doubleValue();
                    double second = 0.0;
                    for (int i=0;i<cs.targets.size();i++) {
                        MethodRef v = cs.targets.get(i).ref;
                        if (!v.equals(vStar)) {
                            double s2 = cs.baseScores[i]; // second-best measured on base for margin
                            if (s2 > second) second = s2;
                        }
                    }

                    boolean accept = (best - Math.max(0, second)) >= io.bytecodemapper.cli.method.MethodScorer.MIN_MARGIN
                            && best >= io.bytecodemapper.cli.method.MethodScorer.TAU_ACCEPT;

                    if (accept) {
                        matched++;
                        pw.println(u.toString() + " -> " + vStar.toString() +
                                " score=" + String.format(java.util.Locale.ROOT, "%.4f", best));
                    } else {
                        abstained++;
                        pw.println("# abstain refined | " + u.toString() + " -> " + vStar.toString() +
                                " score=" + String.format(java.util.Locale.ROOT, "%.4f", best) +
                                " second=" + String.format(java.util.Locale.ROOT, "%.4f", Math.max(0, second)));
                    }
                    total++;
                }
                // <<< AUTOGEN: BYTECODEMAPPER CLI MethodMatch PER CLASS REFINE BLOCK END
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
