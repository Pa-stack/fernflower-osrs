// >>> AUTOGEN: BYTECODEMAPPER signals FieldUsageExtractor BEGIN
package io.bytecodemapper.signals.fields;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class FieldUsageExtractor implements Opcodes {

    public static final class FieldUse {
        public final String owner;   // internal name
        public final String name;
        public final String desc;
        public final boolean write;
        public final boolean isStatic;
        public final int ordinal;    // order index within method

        public FieldUse(String owner, String name, String desc, boolean write, boolean isStatic, int ordinal) {
            this.owner = owner; this.name = name; this.desc = desc;
            this.write = write; this.isStatic = isStatic; this.ordinal = ordinal;
        }

        @Override public String toString() {
            return (isStatic ? "S" : "I") + (write ? "W" : "R") + " " + owner + "." + name + " " + desc + " @" + ordinal;
        }
    }

    /** Extracts field uses in deterministic order from a MethodNode (use post-normalized bodies upstream). */
    public static List<FieldUse> extract(MethodNode mn) {
        List<FieldUse> out = new ArrayList<FieldUse>();
        if (mn == null || mn.instructions == null) return out;
        int ord = 0;
        for (AbstractInsnNode p = mn.instructions.getFirst(); p != null; p = p.getNext()) {
            if (p instanceof FieldInsnNode) {
                FieldInsnNode f = (FieldInsnNode) p;
                boolean write = (f.getOpcode() == PUTFIELD || f.getOpcode() == PUTSTATIC);
                boolean stat  = (f.getOpcode() == GETSTATIC || f.getOpcode() == PUTSTATIC);
                out.add(new FieldUse(f.owner, f.name, f.desc, write, stat, ord));
            }
            ord++;
        }
        return out;
    }

    /** Utility to count reads/writes for a bag of FieldUse events. */
    public static final class RWCounts {
        public int reads; public int writes;
        public RWCounts add(FieldUse u){ if (u.write) writes++; else reads++; return this; }
        public double rwRatio(){ int tot = reads + writes; return tot==0? 0.5 : (reads/(double)tot); } // [0,1], 0.5 neutral
    }

    private FieldUsageExtractor(){}
}
// <<< AUTOGEN: BYTECODEMAPPER signals FieldUsageExtractor END
