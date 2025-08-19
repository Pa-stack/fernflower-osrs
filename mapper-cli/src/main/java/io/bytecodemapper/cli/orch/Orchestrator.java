// >>> AUTOGEN: BYTECODEMAPPER CLI Orchestrator BEGIN
package io.bytecodemapper.cli.orch;

import io.bytecodemapper.cli.util.CliPaths;
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

    public static final class Result {
        public final java.util.Map<String,String> classMap;
        public final java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry> methods;
        public final java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.FieldEntry> fields;
        public final int classesOld;
        public final int classesNew;
        public final int methodsOld;
        public final int methodsNew;

        public Result(java.util.Map<String,String> classMap,
                       java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.MethodEntry> methods,
                       java.util.List<io.bytecodemapper.io.tiny.TinyV2Writer.FieldEntry> fields,
                       int classesOld, int classesNew, int methodsOld, int methodsNew) {
            this.classMap = classMap;
            this.methods = methods;
            this.fields = fields;
            this.classesOld = classesOld;
            this.classesNew = classesNew;
            this.methodsOld = methodsOld;
            this.methodsNew = methodsNew;
        }
    }

    public Result run(Path oldJar, Path newJar, OrchestratorOptions opt) throws Exception {
        if (opt == null) throw new IllegalArgumentException("options");
        // Deterministic mode: avoid parallel ops, keep stable sort order everywhere.
        if (opt.deterministic) {
            System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1");
        }

        // Load classes deterministically
        Map<String, ClassNode> oldClasses = readJarDeterministic(oldJar);
        Map<String, ClassNode> newClasses = readJarDeterministic(newJar);

        // IDF store (persisted across runs)
        Path idfPath = opt.idfPath != null ? opt.idfPath : CliPaths.resolveOutput("build/idf.properties");
        IdfStore idf = IdfStore.load(idfPath.toFile());

        // --- Phase 0: per-method feature extraction (normalize -> CFG -> wl/micro/normalized) ---
        Map<String, Map<String, MethodFeature>> oldFeatures = extractFeatures(oldClasses, opt);
        Map<String, Map<String, MethodFeature>> newFeatures = extractFeatures(newClasses, opt);
        if (opt.debugStats) {
            System.out.println("[Orch] Extracted features: oldClasses=" + oldFeatures.size() + " newClasses=" + newFeatures.size());
        }

        // --- Phase 1/2/3/4: placeholders for matching — to be wired to actual matchers ---
        java.util.Map<String,String> classMap = new java.util.LinkedHashMap<String,String>();
        java.util.List<MethodPair> methodPairs = new java.util.ArrayList<MethodPair>();
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
        oldClasses.size(), newClasses.size(), countMethods(oldClasses), countMethods(newClasses));
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
            Map<String, ClassNode> classes, OrchestratorOptions opt) throws Exception {
        Map<String, Map<String, MethodFeature>> out = new TreeMap<String, Map<String, MethodFeature>>();
        List<String> owners = new ArrayList<String>(classes.keySet());
        Collections.sort(owners);
        for (String owner : owners) {
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
                    if ((mn.access & (org.objectweb.asm.Opcodes.ACC_ABSTRACT | org.objectweb.asm.Opcodes.ACC_NATIVE)) != 0) {
                        continue;
                    }
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

                    // Stable normalizedBodyHash (SHA-256 of opcodes/operands; skip labels/frames)
                    String normHash = stableInsnHash(mn);

                    MethodFeature feat = new MethodFeature(
                            wlSig.hash,
                            micro,
                            norm.opcodeHistogram,
                            norm.stringConstants,
                            norm.invokedSignatures,
                            norm.normalizedDescriptor,
                            norm.fingerprint,
                            normHash
                    );
                    m.put(mn.name + mn.desc, feat);
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
