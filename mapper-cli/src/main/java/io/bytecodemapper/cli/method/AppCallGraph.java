// >>> AUTOGEN: BYTECODEMAPPER CLI AppCallGraph BEGIN
package io.bytecodemapper.cli.method;

import io.bytecodemapper.core.normalize.Normalizer;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Builds simple app-only **intra-class** call graphs.
 * For class Owner, we collect edges (caller -> callee) only if callee.owner == Owner.
 */
public final class AppCallGraph {

    /** Deterministic: MethodRef -> Set<MethodRef> of called same-class methods. */
    public static Map<MethodRef, Set<MethodRef>> buildIntraClassGraph(ClassNode owner) {
        Map<MethodRef, Set<MethodRef>> g = new LinkedHashMap<MethodRef, Set<MethodRef>>();
        if (owner.methods == null) return g;

        List<MethodNode> methods = new ArrayList<MethodNode>(owner.methods);
        Collections.sort(methods, new Comparator<MethodNode>() {
            public int compare(MethodNode a, MethodNode b) {
                int c = a.name.compareTo(b.name);
                return c != 0 ? c : a.desc.compareTo(b.desc);
            }
        });

        for (MethodNode raw : methods) {
            Normalizer.Result norm = Normalizer.normalize(raw, Normalizer.Options.defaults());
            MethodNode mn = norm.method;

            MethodRef caller = new MethodRef(owner.name, mn.name, mn.desc);
            Set<MethodRef> succ = new LinkedHashSet<MethodRef>();
            g.put(caller, succ);

            for (AbstractInsnNode p = mn.instructions.getFirst(); p != null; p = p.getNext()) {
                if (p instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) p;
                    if (owner.name.equals(m.owner)) {
                        succ.add(new MethodRef(m.owner, m.name, m.desc));
                    }
                }
            }
        }
        return g;
    }

    private AppCallGraph(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI AppCallGraph END
