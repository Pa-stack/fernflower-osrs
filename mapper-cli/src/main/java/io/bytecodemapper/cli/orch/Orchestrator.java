// >>> AUTOGEN: BYTECODEMAPPER CLI Orchestrator BEGIN
package io.bytecodemapper.cli.orch;

import io.bytecodemapper.cli.util.CliPaths;
import io.bytecodemapper.cli.cache.MethodFeatureCache;
import io.bytecodemapper.cli.cache.MethodFeatureCacheEntry;
import io.bytecodemapper.core.match.MethodMatcher;
import io.bytecodemapper.core.match.MethodMatcher.MethodMatchResult;
import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import io.bytecodemapper.core.df.DF;
import io.bytecodemapper.core.wl.WLRefinement;
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
import io.bytecodemapper.signals.micro.MicroPatternExtractor;
import io.bytecodemapper.signals.normalized.NormalizedMethod;
import io.bytecodemapper.signals.idf.IdfStore;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Path;
import java.util.*;

public final class Orchestrator {
    // >>> AUTOGEN: BYTECODEMAPPER CLI Orchestrator BENCH API BEGIN
    // Result for bench per pair
    public static final class BenchPairResult {
        public final String tag;
        public final int acceptedMethods;
        public final int abstainedMethods;
        public final int acceptedClasses;
        public final int ambiguousCount;
        public BenchPairResult(String tag, int am, int as, int ac, int amb){
            this.tag = tag; this.acceptedMethods=am; this.abstainedMethods=as; this.acceptedClasses=ac; this.ambiguousCount=amb;
        }
    }

    // store middle-jar coverage per pair for churn/oscillation (by tag)
    private final java.util.Map<String, java.util.Set<String>> newSideMethodIdsByPair = new java.util.HashMap<String, java.util.Set<String>>();
    private final java.util.Map<String, java.util.Set<String>> oldSideMethodIdsByPair = new java.util.HashMap<String, java.util.Set<String>>();

    public java.util.Set<String> getNewSideMethodIds(String pairTag){
        java.util.Set<String> s = newSideMethodIdsByPair.get(pairTag);
        return (s==null)? java.util.Collections.<String>emptySet() : s;
    }
    public java.util.Set<String> getOldSideMethodIds(String pairTag){
        java.util.Set<String> s = oldSideMethodIdsByPair.get(pairTag);
        return (s==null)? java.util.Collections.<String>emptySet() : s;
    }

    /** Run a single pair mapping for bench and record middle-jar coverage sets. */
    public BenchPairResult mapPairForBench(java.nio.file.Path oldJar, java.nio.file.Path newJar, OrchestratorOptions opt) throws Exception {
        final String tag = (oldJar.getFileName()!=null?oldJar.getFileName().toString():"old") + "→" + (newJar.getFileName()!=null?newJar.getFileName().toString():"new");

        // Run the standard pipeline and adapt stats from current Result structure
        Result r = run(oldJar, newJar, opt);

        // Build the sets in a stable representation: owner#name(desc) with jar-side prefix to avoid collision.
        java.util.Set<String> newSide = new java.util.TreeSet<String>();
        java.util.Set<String> oldSide = new java.util.TreeSet<String>();
        if (r != null && r.methods != null) {
            for (io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry me : r.methods) {
                // OLD side uses original owner/name/desc
                oldSide.add("OLD:" + me.ownerFrom + "#" + me.nameFrom + me.desc);
                // NEW side uses mapped owner if class renamed, else original; and mapped name
                String ownerTo = r.classMap != null && r.classMap.containsKey(me.ownerFrom) ? r.classMap.get(me.ownerFrom) : me.ownerFrom;
                newSide.add("NEW:" + ownerTo + "#" + me.nameTo + me.desc);
            }
        }
        newSideMethodIdsByPair.put(tag, newSide);
        oldSideMethodIdsByPair.put(tag, oldSide);

        // Counts
        int acceptedMethods = r != null && r.methods != null ? r.methods.size() : 0;
        int acceptedClasses = r != null && r.classMap != null ? r.classMap.size() : 0;

        // Compute abstained methods as (old methods in mapped classes) - (accepted matches in those classes)
        int abstainedMethods = 0;
        if (r != null && r.classMap != null && !r.classMap.isEmpty()) {
            // Build per-class accepted counts
            java.util.Map<String,Integer> acceptedPerOwner = new java.util.HashMap<String,Integer>();
            if (r.methods != null) {
                for (io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry me : r.methods) {
                    Integer c = acceptedPerOwner.get(me.ownerFrom);
                    acceptedPerOwner.put(me.ownerFrom, c == null ? 1 : (c + 1));
                }
            }
            // Load classes to count total eligible methods per mapped owner
            java.util.Map<String, org.objectweb.asm.tree.ClassNode> oldClasses = readJarDeterministic(oldJar);
            for (String oldOwner : r.classMap.keySet()) {
                org.objectweb.asm.tree.ClassNode cn = oldClasses.get(oldOwner);
                if (cn == null || cn.methods == null) continue;
                int total = 0;
                for (org.objectweb.asm.tree.MethodNode mn : (java.util.List<org.objectweb.asm.tree.MethodNode>) cn.methods) {
                    if ((mn.access & (org.objectweb.asm.Opcodes.ACC_ABSTRACT | org.objectweb.asm.Opcodes.ACC_NATIVE)) != 0) continue;
                    total++;
                }
                int acc = acceptedPerOwner.get(oldOwner) == null ? 0 : acceptedPerOwner.get(oldOwner);
                abstainedMethods += Math.max(0, total - acc);
            }
        }

        // With the current exact-signature matcher, ambiguity doesn't occur
        int ambiguous = 0;

        return new BenchPairResult(tag, acceptedMethods, abstainedMethods, acceptedClasses, ambiguous);
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI Orchestrator BENCH API END

    public static final class Result {
        public final java.util.Map<String,String> classMap;
        public final java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry> methods;
        public final java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.FieldEntry> fields;
        public final int classesOld;
        public final int classesNew;
        public final int methodsOld;
        public final int methodsNew;
    // Candidate set stats (medians/p95) computed deterministically
    public final int candCountExactMedian;
    public final int candCountExactP95;
    public final int candCountNearMedian;
    public final int candCountNearP95;
    // Telemetry: number of accepted matches where the winning candidate came from WL-relaxed
    public final int wlRelaxedHits;
    // Echo per-run WL-relaxed thresholds for observability
    public final int wlRelaxedL1;
    public final double wlSizeBand;
    // Alias field to match spec naming in DTO for external readers
    public final double wlRelaxedSizeBand;

        public Result(java.util.Map<String,String> classMap,
                       java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry> methods,
                       java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.FieldEntry> fields,
               int classesOld, int classesNew, int methodsOld, int methodsNew,
               int candCountExactMedian, int candCountExactP95,
                    int candCountNearMedian, int candCountNearP95,
                            int wlRelaxedHits,
                            int wlRelaxedL1,
                            double wlSizeBand) {
            this.classMap = classMap;
            this.methods = methods;
            this.fields = fields;
            this.classesOld = classesOld;
            this.classesNew = classesNew;
            this.methodsOld = methodsOld;
            this.methodsNew = methodsNew;
        this.candCountExactMedian = candCountExactMedian;
        this.candCountExactP95 = candCountExactP95;
        this.candCountNearMedian = candCountNearMedian;
        this.candCountNearP95 = candCountNearP95;
                this.wlRelaxedHits = wlRelaxedHits;
                        this.wlRelaxedL1 = wlRelaxedL1;
                        this.wlSizeBand = wlSizeBand;
                        this.wlRelaxedSizeBand = wlSizeBand;
        }
    }

    public Result run(Path oldJar, Path newJar, OrchestratorOptions opt) throws Exception {
        if (opt == null) throw new IllegalArgumentException("options");
        // Deterministic mode: avoid parallel ops, keep stable sort order everywhere.
        if (opt.deterministic) {
            System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1");
        }

        // Configure scoring weights/toggles globally (if MethodScorer used downstream)
        try {
            io.bytecodemapper.cli.method.MethodScorer.configureWeights(
                opt.weightCalls, opt.weightMicropatterns, opt.weightOpcode, opt.weightStrings, opt.weightFields, opt.useNormalizedHistogram);
            io.bytecodemapper.cli.method.MethodScorer.setAlphaMicropattern(opt.alphaMicropattern);
            io.bytecodemapper.cli.method.MethodScorer.setTauAccept(opt.tauAccept);
        } catch (Throwable ignore) {
            // scorer may not be invoked in current placeholder pipeline; keep forward compatibility silently
        }

        // Load classes deterministically
        Map<String, ClassNode> oldClasses = readJarDeterministic(oldJar);
        Map<String, ClassNode> newClasses = readJarDeterministic(newJar);

        // IDF store (persisted across runs)
        Path idfPath = opt.idfPath != null ? opt.idfPath : CliPaths.resolveOutput("build/idf.properties");
        IdfStore idf = IdfStore.load(idfPath.toFile());

        // --- Phase 0: per-method feature extraction (normalize -> CFG -> wl/micro/normalized) with persistent cache ---
        String oldKey = jarKey(oldJar);
        String newKey = jarKey(newJar);
        // Compute IR fingerprint (Normalizer + ReducedCFG) and persist per-jar cache metadata
        io.bytecodemapper.core.normalize.Normalizer.Options nopt = io.bytecodemapper.core.normalize.Normalizer.Options.defaults();
        io.bytecodemapper.core.cfg.ReducedCFG.Options copt = io.bytecodemapper.core.cfg.ReducedCFG.Options.defaults();
        final String irFp = io.bytecodemapper.core.ir.IRFingerprint.compose(nopt, copt);
        final String irVersion = io.bytecodemapper.core.normalize.NormalizerFingerprint.NORMALIZER_VERSION + "+" +
                io.bytecodemapper.core.cfg.ReducedCfgFingerprint.CFG_VERSION;
        try {
            io.bytecodemapper.cli.cache.CacheMeta.write(opt.cacheDir, oldKey, irVersion, irFp);
            io.bytecodemapper.cli.cache.CacheMeta.write(opt.cacheDir, newKey, irVersion, irFp);
        } catch (Exception metaEx) {
            if (opt.debugStats) System.out.println("[Orch] Cache meta write failed: " + metaEx.getMessage());
        }
        MethodFeatureCache oldCache = MethodFeatureCache.open(opt.cacheDir, oldKey);
        MethodFeatureCache newCache = MethodFeatureCache.open(opt.cacheDir, newKey);
        Map<String, Map<String, MethodFeature>> oldFeatures = null;
        Map<String, Map<String, MethodFeature>> newFeatures = null;
        try {
            oldFeatures = extractFeatures(oldClasses, opt, oldCache, irFp);
            newFeatures = extractFeatures(newClasses, opt, newCache, irFp);
        } finally {
            // Flush caches deterministically
            try { oldCache.close(); } catch (Exception ignored) {}
            try { newCache.close(); } catch (Exception ignored) {}
        }
        if (opt.debugStats) {
            System.out.println("[Orch] Extracted features: oldClasses=" + oldFeatures.size() + " newClasses=" + newFeatures.size());
        }

        // --- Phase 1/2/3/4: basic matching — identity class map + signature equality for methods ---
        java.util.Map<String,String> classMap = new java.util.LinkedHashMap<String,String>();
        {
            java.util.List<String> olds = new java.util.ArrayList<String>(oldClasses.keySet());
            java.util.Collections.sort(olds);
            for (String o : olds) if (newClasses.containsKey(o)) classMap.put(o, o);
        }

        // Convert features to cache-entry-shaped maps (keys are what matcher needs)
        java.util.Map<String, java.util.Map<String, MethodFeatureCacheEntry>> oldFeat = new java.util.LinkedHashMap<String, java.util.Map<String, MethodFeatureCacheEntry>>();
        java.util.Map<String, java.util.Map<String, MethodFeatureCacheEntry>> newFeat = new java.util.LinkedHashMap<String, java.util.Map<String, MethodFeatureCacheEntry>>();
        for (java.util.Map.Entry<String, java.util.Map<String, MethodFeature>> e : oldFeatures.entrySet()) {
            java.util.Map<String, MethodFeatureCacheEntry> m = new java.util.LinkedHashMap<String, MethodFeatureCacheEntry>();
            for (java.util.Map.Entry<String, MethodFeature> mf : e.getValue().entrySet()) {
                MethodFeature f = mf.getValue();
                m.put(mf.getKey(), new MethodFeatureCacheEntry(
                        f.wlSig, f.micro, f.opcodeHistogram, f.stringConstants, f.invokedSignatures,
                        f.normalizedDescriptor, f.fingerprint, f.normalizedBodyHash));
            }
            oldFeat.put(e.getKey(), m);
        }
        for (java.util.Map.Entry<String, java.util.Map<String, MethodFeature>> e : newFeatures.entrySet()) {
            java.util.Map<String, MethodFeatureCacheEntry> m = new java.util.LinkedHashMap<String, MethodFeatureCacheEntry>();
            for (java.util.Map.Entry<String, MethodFeature> mf : e.getValue().entrySet()) {
                MethodFeature f = mf.getValue();
                m.put(mf.getKey(), new MethodFeatureCacheEntry(
                        f.wlSig, f.micro, f.opcodeHistogram, f.stringConstants, f.invokedSignatures,
                        f.normalizedDescriptor, f.fingerprint, f.normalizedBodyHash));
            }
            newFeat.put(e.getKey(), m);
        }

        java.util.List<MethodPair> methodPairs = new java.util.ArrayList<MethodPair>();
        int exactMedian = 0, exactP95 = 0, nearMedian = 0, nearP95 = 0;
        int wlRelaxedHits = 0;
        {
            io.bytecodemapper.core.match.MethodMatcher.MethodMatcherOptions mopts = new io.bytecodemapper.core.match.MethodMatcher.MethodMatcherOptions();
            mopts.wlRelaxedL1 = opt.wlRelaxedL1;
            mopts.wlSizeBand = opt.wlSizeBand;
            MethodMatchResult mm = MethodMatcher.matchMethods(oldClasses, newClasses, classMap, oldFeat, newFeat, idf, mopts, opt.deterministic, opt.debugStats);
            for (MethodMatcher.Pair p : mm.accepted) methodPairs.add(new MethodPair(p.oldOwner, p.oldName, p.desc, p.newName));
            // Aggregate stats deterministically
            exactMedian = percentile(mm.exactCounts, 50);
            exactP95    = percentile(mm.exactCounts, 95);
            nearMedian  = percentile(mm.nearCounts, 50);
            nearP95     = percentile(mm.nearCounts, 95);
            // carry telemetry to result
            wlRelaxedHits = mm.wlRelaxedHits;
        }
        java.util.List<FieldPair> fieldPairs = new java.util.ArrayList<FieldPair>();

    // --- Phase 5: prepare Tiny v2 mapping entries deterministically ---
    // classes ordered by key
    java.util.List<String> cmKeys = new java.util.ArrayList<String>(classMap.keySet());
    java.util.Collections.sort(cmKeys);
    java.util.Map<String,String> tinyClasses = new java.util.LinkedHashMap<String,String>();
    for (String o : cmKeys) tinyClasses.put(o, classMap.get(o));
    // methods
    java.util.Collections.sort(methodPairs, new java.util.Comparator<MethodPair>() {
            public int compare(MethodPair a, MethodPair b) {
                int c = a.oldOwner.compareTo(b.oldOwner); if (c!=0) return c;
                c = a.desc.compareTo(b.desc); if (c!=0) return c;
                return a.oldName.compareTo(b.oldName);
            }
        });
    java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry> tinyMethods = new java.util.ArrayList<io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry>();
    for (MethodPair p : methodPairs) tinyMethods.add(new io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry(p.oldOwner, p.oldName, p.desc, p.newName));
    // fields
    java.util.Collections.sort(fieldPairs, new java.util.Comparator<FieldPair>() {
            public int compare(FieldPair a, FieldPair b) {
                int c = a.oldOwner.compareTo(b.oldOwner); if (c!=0) return c;
                c = a.desc.compareTo(b.desc); if (c!=0) return c;
                return a.oldName.compareTo(b.oldName);
            }
        });
    java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.FieldEntry> tinyFields = new java.util.ArrayList<io.bytecodemapper.io.tiny.TinyV2Writer.FieldEntry>();
    for (FieldPair p : fieldPairs) tinyFields.add(new io.bytecodemapper.io.tiny.TinyV2Writer.FieldEntry(p.oldOwner, p.oldName, p.desc, p.newName));

    // Persist IDF (no update logic yet, just ensure file exists)
    idf.save(idfPath.toFile());

    return new Result(tinyClasses, tinyMethods, tinyFields,
        oldClasses.size(), newClasses.size(), countMethods(oldClasses), countMethods(newClasses),
        exactMedian, exactP95, nearMedian, nearP95,
        wlRelaxedHits,
        opt.wlRelaxedL1,
        opt.wlSizeBand);
    }

    // Deterministic percentile: sort copy ascending; for p in [0,100], use nearest-rank (ceil) index
    static int percentile(java.util.List<Integer> values, int p) {
        if (values == null || values.isEmpty()) return 0;
        java.util.ArrayList<Integer> xs = new java.util.ArrayList<Integer>(values);
        java.util.Collections.sort(xs);
        int n = xs.size();
        if (p <= 0) return xs.get(0).intValue();
        if (p >= 100) return xs.get(n-1).intValue();
        // nearest-rank: ceil(p/100 * n)
        int rank = (int) Math.ceil((p / 100.0) * n);
        int idx = Math.min(Math.max(rank - 1, 0), n - 1);
        return xs.get(idx).intValue();
    }

    // Minimal report JSON writer (deterministic key order)
    public static void writeReportJson(java.nio.file.Path out, Result r) throws Exception {
        if (out == null || r == null) return;
        if (out.getParent() != null) java.nio.file.Files.createDirectories(out.getParent());
        java.io.BufferedWriter bw = java.nio.file.Files.newBufferedWriter(out, java.nio.charset.StandardCharsets.UTF_8);
        try {
            // { "candidate_stats": { "cand_count_exact_median": X, ... } }
            bw.write('{');
            bw.write("\"candidate_stats\":{");
            bw.write("\"cand_count_exact_median\":" + r.candCountExactMedian);
            bw.write(',');
            bw.write("\"cand_count_exact_p95\":" + r.candCountExactP95);
            bw.write(',');
            bw.write("\"cand_count_near_median\":" + r.candCountNearMedian);
            bw.write(',');
            bw.write("\"cand_count_near_p95\":" + r.candCountNearP95);
            bw.write('}');
            bw.write(',');
            // Emit thresholds first in fixed order, then hits
            bw.write("\"wl_relaxed_l1\":" + r.wlRelaxedL1);
            bw.write(',');
            // write wl_relaxed_size_band with 2 decimal places for readability
            java.text.DecimalFormatSymbols sym = new java.text.DecimalFormatSymbols(java.util.Locale.ROOT);
            sym.setDecimalSeparator('.');
            java.text.DecimalFormat df = new java.text.DecimalFormat("0.00", sym);
            bw.write("\"wl_relaxed_size_band\":" + df.format(r.wlRelaxedSizeBand));
            bw.write(',');
            bw.write("\"wl_relaxed_hits\":" + r.wlRelaxedHits);
            bw.write('}');
            bw.newLine();
        } finally {
            try { bw.close(); } catch (Exception ignore) {}
        }
    }

    // --- Feature container used by this orchestrator ---
    public static final class MethodFeature {
        public final long wlSig;
        public final java.util.BitSet micro;
        public final java.util.Map<Integer,Integer> opcodeHistogram;
        public final java.util.Set<String> stringConstants;
        public final java.util.Set<String> invokedSignatures;
        public final java.lang.String normalizedDescriptor;
        public final java.lang.String fingerprint;
        public final java.lang.String normalizedBodyHash;
        public MethodFeature(long wlSig, java.util.BitSet micro,
                             java.util.Map<Integer,Integer> opcodeHistogram,
                             java.util.Set<String> stringConstants,
                             java.util.Set<String> invokedSignatures,
                             java.lang.String normalizedDescriptor,
                             java.lang.String fingerprint,
                             java.lang.String normalizedBodyHash) {
            this.wlSig = wlSig;
            this.micro = micro;
            this.opcodeHistogram = opcodeHistogram;
            this.stringConstants = stringConstants;
            this.invokedSignatures = invokedSignatures;
            this.normalizedDescriptor = normalizedDescriptor;
            this.fingerprint = fingerprint;
            this.normalizedBodyHash = normalizedBodyHash;
        }
    }

    public static final class MethodPair {
        public final String oldOwner, oldName, desc, newName;
        public MethodPair(String oldOwner, String oldName, String desc, String newName) {
            this.oldOwner = oldOwner; this.oldName = oldName; this.desc = desc; this.newName = newName;
        }
    }
    public static final class FieldPair {
        public final String oldOwner, oldName, desc, newName;
        public FieldPair(String oldOwner, String oldName, String desc, String newName) {
            this.oldOwner = oldOwner; this.oldName = oldName; this.desc = desc; this.newName = newName;
        }
    }

    private Map<String, Map<String, MethodFeature>> extractFeatures(
        Map<String, ClassNode> classes, OrchestratorOptions opt, MethodFeatureCache cache, String irFp) throws Exception {
        Map<String, Map<String, MethodFeature>> out = new TreeMap<String, Map<String, MethodFeature>>();
        List<String> owners = new ArrayList<String>(classes.keySet());
        Collections.sort(owners);
        final int cap = opt != null ? Math.max(0, opt.maxMethods) : 0;
        int processed = 0;
        for (String owner : owners) {
            if (cap > 0 && processed >= cap) break;
            ClassNode cn = classes.get(owner);
            Map<String, MethodFeature> m = new TreeMap<String, MethodFeature>();
            if (cn.methods != null) {
                // deterministic method order: by (name, desc)
                List<MethodNode> methods = new ArrayList<MethodNode>(cn.methods);
                Collections.sort(methods, new Comparator<MethodNode>() {
                    public int compare(MethodNode a, MethodNode b) {
                        int c = a.name.compareTo(b.name); if (c!=0) return c;
                        return a.desc.compareTo(b.desc);
                    }
                });
                for (MethodNode mn : methods) {
                    if (cap > 0 && processed >= cap) break;
                    if ((mn.access & (org.objectweb.asm.Opcodes.ACC_ABSTRACT | org.objectweb.asm.Opcodes.ACC_NATIVE)) != 0) {
                        continue;
                    }
            // Compute normalized body hash first for cache key
            String normHash = stableInsnHash(mn);
            String cacheKey = owner + "::" + mn.name + mn.desc + "::" + normHash + "::" + (irFp != null ? irFp : "");
            MethodFeatureCacheEntry ce = cache != null ? cache.get(cacheKey) : null;
            MethodFeature feat;
            if (ce != null) {
            // Rehydrate from cache
            feat = new MethodFeature(
                ce.wlSignature,
                ce.microBits,
                ce.normOpcodeHistogram,
                ce.strings,
                ce.invokedSignatures,
                ce.normalizedDescriptor,
                ce.normFingerprint,
                ce.normalizedBodyHash
            );
            } else {
            // Normalize inside ReducedCFG.build (already integrated) for analysis alignment
            ReducedCFG cfg = ReducedCFG.build(mn);
            Dominators dom = Dominators.compute(cfg);
            java.util.Map<Integer,int[]> df = DF.compute(cfg, dom);
            java.util.Map<Integer,int[]> tdf = DF.iterateToFixpoint(df);
            WLRefinement.MethodSignature wlSig = WLRefinement.computeSignature(cfg, dom, df, tdf, 3);

            // Micropatterns (owner-aware; back-edge loop)
            java.util.BitSet micro = MicroPatternExtractor.extract(owner, mn, cfg, dom);

            // NormalizedMethod features (generalized histogram & fingerprint)
            NormalizedMethod norm = new NormalizedMethod(owner, mn, java.util.Collections.<Integer>emptySet());

            feat = new MethodFeature(
                wlSig.hash,
                micro,
                norm.opcodeHistogram,
                norm.stringConstants,
                norm.invokedSignatures,
                norm.normalizedDescriptor,
                norm.fingerprint,
                normHash
            );
            if (cache != null) {
                MethodFeatureCacheEntry ne = new MethodFeatureCacheEntry(
                    wlSig.hash,
                    micro,
                    norm.opcodeHistogram,
                    norm.stringConstants,
                    norm.invokedSignatures,
                    norm.normalizedDescriptor,
                    norm.fingerprint,
                    normHash
                );
                cache.put(cacheKey, ne);
            }
            }
            m.put(mn.name + mn.desc, feat);
            processed++;
                }
            }
            out.put(owner, m);
        }
        return out;
    }

    private static Map<String, ClassNode> readJarDeterministic(Path jar) throws Exception {
        final java.util.Map<String, ClassNode> map = new java.util.TreeMap<String, ClassNode>();
    ClasspathScanner scanner = new ClasspathScanner();
    scanner.scan(jar.toFile(), new io.bytecodemapper.core.fingerprint.ClasspathScanner.Sink() {
            public void accept(ClassNode cn) { map.put(cn.name, cn); }
        });
        return map;
    }

    private static int countMethods(Map<String, ClassNode> classes) {
        int n = 0;
        for (ClassNode cn : classes.values()) if (cn.methods != null) n += cn.methods.size();
        return n;
    }

    private static String jarKey(Path jar) throws Exception {
        String abs = jar.toFile().getCanonicalPath();
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        byte[] d = md.digest(abs.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        String name = jar.getFileName() != null ? jar.getFileName().toString() : "jar";
        return name.replace('.', '_') + "-" + sb.toString();
    }

    private static String stableInsnHash(MethodNode mn) throws Exception {
        StringBuilder sb = new StringBuilder(256);
        for (org.objectweb.asm.tree.AbstractInsnNode insn : mn.instructions.toArray()) {
            int op = insn.getOpcode();
            if (op < 0) continue;
            sb.append(op).append(':');
            if (insn instanceof org.objectweb.asm.tree.IntInsnNode) {
                sb.append(((org.objectweb.asm.tree.IntInsnNode) insn).operand);
            } else if (insn instanceof org.objectweb.asm.tree.VarInsnNode) {
                sb.append(((org.objectweb.asm.tree.VarInsnNode) insn).var);
            } else if (insn instanceof org.objectweb.asm.tree.TypeInsnNode) {
                sb.append(((org.objectweb.asm.tree.TypeInsnNode) insn).desc);
            } else if (insn instanceof org.objectweb.asm.tree.FieldInsnNode) {
                org.objectweb.asm.tree.FieldInsnNode fi = (org.objectweb.asm.tree.FieldInsnNode) insn;
                sb.append(fi.owner).append('#').append(fi.name).append(':').append(fi.desc);
            } else if (insn instanceof org.objectweb.asm.tree.MethodInsnNode) {
                org.objectweb.asm.tree.MethodInsnNode mi = (org.objectweb.asm.tree.MethodInsnNode) insn;
                sb.append(mi.owner).append('#').append(mi.name).append(mi.desc);
            } else if (insn instanceof org.objectweb.asm.tree.LdcInsnNode) {
                Object c = ((org.objectweb.asm.tree.LdcInsnNode) insn).cst;
                sb.append(String.valueOf(c));
            } else if (insn instanceof org.objectweb.asm.tree.IincInsnNode) {
                org.objectweb.asm.tree.IincInsnNode ii = (org.objectweb.asm.tree.IincInsnNode) insn;
                sb.append(ii.var).append(',').append(ii.incr);
            } else if (insn instanceof org.objectweb.asm.tree.TableSwitchInsnNode) {
                org.objectweb.asm.tree.TableSwitchInsnNode ts = (org.objectweb.asm.tree.TableSwitchInsnNode) insn;
                sb.append(ts.min).append(',').append(ts.max);
            } else if (insn instanceof org.objectweb.asm.tree.LookupSwitchInsnNode) {
                org.objectweb.asm.tree.LookupSwitchInsnNode ls = (org.objectweb.asm.tree.LookupSwitchInsnNode) insn;
                java.util.List<?> keys = ls.keys; sb.append(keys!=null?keys.size():0);
            }
            sb.append('|');
        }
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(sb.toString().getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder(64);
        for (byte b : d) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Orchestrator END
