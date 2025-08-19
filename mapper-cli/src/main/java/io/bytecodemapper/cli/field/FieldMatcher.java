// >>> AUTOGEN: BYTECODEMAPPER CLI FieldMatcher BEGIN
package io.bytecodemapper.cli.field;

import io.bytecodemapper.cli.method.MethodRef;
import io.bytecodemapper.core.normalize.Normalizer;
import io.bytecodemapper.signals.fields.FieldUsageExtractor;
import io.bytecodemapper.signals.fields.FieldUsageExtractor.FieldUse;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

/**
 * Conservative field matcher using co-occurrence across matched method pairs,
 * read/write ratio similarity, and mapped-owner consistency.
 */
public final class FieldMatcher {

    public static final int MIN_SUPPORT = 3;           // min #matched-method pairs voting for a field pair
    public static final int MIN_MARGIN  = 2;           // best - second best support
    public static final double TAU_RATIO_SIM = 0.60;   // min RW-ratio similarity

    public static final class VoteAgg {
        public int support = 0;
        public int readsOld = 0, writesOld = 0;
        public int readsNew = 0, writesNew = 0;
        public void addOld(FieldUse u){ if (u.write) writesOld++; else readsOld++; }
        public void addNew(FieldUse u){ if (u.write) writesNew++; else readsNew++; }
        public double ratioSim(){
            int oTot = readsOld + writesOld, nTot = readsNew + writesNew;
            double o = (oTot==0? 0.5 : readsOld/(double)oTot);
            double n = (nTot==0? 0.5 : readsNew/(double)nTot);
            return 1.0 - Math.abs(o - n); // [0,1]
        }
        public double score(){
            // frequency-heavy, gated by ratio similarity
            double sim = ratioSim();
            return support * (0.5 + 0.5*sim); // conservative monotone
        }
    }

    /** Compute matches per old field given method matches and ASM class nodes. */
    public static Map<FieldRef, FieldRef> matchFields(
            Map<String,String> classMapOldToNew,
            Map<MethodRef, MethodRef> methodMap,
            Map<String, ClassNode> oldClasses,  // internal name -> CN
            Map<String, ClassNode> newClasses) {

        // Aggregate votes: oldField -> (newField -> VoteAgg)
        Map<FieldRef, Map<FieldRef, VoteAgg>> votes = new LinkedHashMap<FieldRef, Map<FieldRef, VoteAgg>>();

        // Index class nodes
        for (Map.Entry<MethodRef, MethodRef> e : methodMap.entrySet()) {
            MethodRef mOld = e.getKey();
            MethodRef mNew = e.getValue();

            ClassNode cOld = oldClasses.get(mOld.owner);
            ClassNode cNew = newClasses.get(mNew.owner);
            if (cOld == null || cNew == null) continue;

            MethodNode mnOld = find(cOld, mOld.name, mOld.desc);
            MethodNode mnNew = find(cNew, mNew.name, mNew.desc);
            if (mnOld == null || mnNew == null) continue;

            // >>> AUTOGEN: BYTECODEMAPPER CLI FieldMatcher NORMALIZE BEFORE EXTRACT BEGIN
            // Normalize method bodies to keep feature space aligned with analysis CFG
            Normalizer.Result nOld = Normalizer.normalize(mnOld, Normalizer.Options.defaults());
            Normalizer.Result nNew = Normalizer.normalize(mnNew, Normalizer.Options.defaults());
            java.util.List<FieldUse> usesOld = FieldUsageExtractor.extract(nOld.method);
            java.util.List<FieldUse> usesNew = FieldUsageExtractor.extract(nNew.method);
            // <<< AUTOGEN: BYTECODEMAPPER CLI FieldMatcher NORMALIZE BEFORE EXTRACT END

            // Restrict to fields whose owners are either the class itself or a mapped owner (more conservative)
            Set<String> allowedNewOwners = new LinkedHashSet<String>();
            allowedNewOwners.add(mNew.owner);
            // If old field owner maps to some new owner, include that
            for (Map.Entry<String,String> cm : classMapOldToNew.entrySet()) {
                if (cm.getKey().equals(mOld.owner)) { allowedNewOwners.add(cm.getValue()); }
            }

            // Tally co-usage within this method pair
            for (FieldUse fo : usesOld) {
                FieldRef foRef = new FieldRef(fo.owner, fo.name, fo.desc);

                for (FieldUse fn : usesNew) {
                    if (!allowedNewOwners.contains(fn.owner)) continue;

                    FieldRef fnRef = new FieldRef(fn.owner, fn.name, fn.desc);

                    Map<FieldRef, VoteAgg> inner = votes.get(foRef);
                    if (inner == null) { inner = new LinkedHashMap<FieldRef, VoteAgg>(); votes.put(foRef, inner); }
                    VoteAgg agg = inner.get(fnRef);
                    if (agg == null) { agg = new VoteAgg(); inner.put(fnRef, agg); }
                    agg.support++;
                    agg.addOld(fo); agg.addNew(fn);
                }
            }
        }

        // Decide matches per old field with conservative gates
        Map<FieldRef, FieldRef> out = new LinkedHashMap<FieldRef, FieldRef>();
        for (Map.Entry<FieldRef, Map<FieldRef, VoteAgg>> e : votes.entrySet()) {
            FieldRef oldF = e.getKey();
            Map<FieldRef, VoteAgg> cand = e.getValue();
            if (cand.isEmpty()) continue;

            // pick best by support, then score; compute margin
            FieldRef best = null;
            int bestSup = -1, secondSup = -1;
            double bestScore = -1.0;

            for (Map.Entry<FieldRef, VoteAgg> c : cand.entrySet()) {
                int sup = c.getValue().support;
                double sc = c.getValue().score();
                if (sup > bestSup || (sup==bestSup && sc > bestScore)) {
                    secondSup = bestSup;
                    bestSup = sup; bestScore = sc; best = c.getKey();
                } else if (sup > secondSup) {
                    secondSup = sup;
                }
            }

            if (best == null) continue;
            VoteAgg aggBest = cand.get(best);
            double ratioSim = aggBest.ratioSim();

            // gates
            boolean ok = bestSup >= MIN_SUPPORT
                      && (bestSup - Math.max(0, secondSup)) >= MIN_MARGIN
                      && ratioSim >= TAU_RATIO_SIM
                      && ownerConsistent(oldF, best, classMapOldToNew);

            if (ok) out.put(oldF, best);
        }
        return out;
    }

    private static boolean ownerConsistent(FieldRef oldF, FieldRef newF, Map<String,String> clsMap) {
        String mapped = clsMap.get(oldF.owner);
        if (mapped == null) return false;
        return mapped.equals(newF.owner);
    }

    private static MethodNode find(ClassNode cn, String name, String desc) {
        for (Object o : cn.methods) {
            org.objectweb.asm.tree.MethodNode m = (org.objectweb.asm.tree.MethodNode) o;
            if (name.equals(m.name) && desc.equals(m.desc)) return m;
        }
        return null;
    }

    private FieldMatcher(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI FieldMatcher END
