// >>> AUTOGEN: BYTECODEMAPPER TEST NormalizerTest BEGIN
package io.bytecodemapper.core;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.normalize.Normalizer;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.Assert.*;

public class NormalizerTest implements Opcodes {

    private static MethodNode mnInit() {
        return new MethodNode(ACC_PUBLIC | ACC_STATIC, "m", "()V", null, null);
    }

    /** ICONST_0 ; IFNE Lbad ; NOP ; GOTO Lend ; Lbad: NEW RuntimeException ; DUP ; INVOKESPECIAL ; ATHROW ; Lend: RETURN */
    private static MethodNode opaqueIfFalseWithWrapper() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions = new InsnList();
        LabelNode Lbad = new LabelNode();
        LabelNode Lend = new LabelNode();

        in.add(new InsnNode(ICONST_0));
        in.add(new JumpInsnNode(IFNE, Lbad));
        in.add(new InsnNode(NOP));
        in.add(new JumpInsnNode(GOTO, Lend));
        in.add(Lbad);
        in.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
        in.add(new InsnNode(DUP));
        in.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false));
        in.add(new InsnNode(ATHROW));
        in.add(Lend);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    /** ICONST_2 ; ICONST_2 ; IF_ICMPNE Lbad ; RETURN ; Lbad: RETURN */
    private static MethodNode constConstCompare() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions = new InsnList();
        LabelNode Lbad = new LabelNode();
        in.add(new InsnNode(ICONST_2));
        in.add(new InsnNode(ICONST_2));
        in.add(new JumpInsnNode(IF_ICMPNE, Lbad));
        in.add(new InsnNode(RETURN));
        in.add(Lbad);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    /** ICONST_1 ; TABLESWITCH 0..2 -> L0,L1,L2 ; default Ld ; L0: RETURN ; L1: RETURN ; L2: RETURN ; Ld: RETURN */
    private static MethodNode constTableSwitch() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions = new InsnList();
        LabelNode L0 = new LabelNode(), L1 = new LabelNode(), L2 = new LabelNode(), Ld = new LabelNode();

        in.add(new InsnNode(ICONST_1));
        in.add(new TableSwitchInsnNode(0, 2, Ld, new LabelNode[]{L0, L1, L2}));
        in.add(L0); in.add(new InsnNode(RETURN));
        in.add(L1); in.add(new InsnNode(RETURN));
        in.add(L2); in.add(new InsnNode(RETURN));
        in.add(Ld); in.add(new InsnNode(RETURN));
        return mn;
    }

    /** Flattening-like: early tableswitch with many arms and many GOTOs. */
    private static MethodNode flatteningLike() {
        MethodNode mn = mnInit();
        InsnList in = mn.instructions = new InsnList();
        LabelNode[] cases = new LabelNode[10];
        for (int i=0;i<cases.length;i++) cases[i] = new LabelNode();
        LabelNode Ld = new LabelNode();
        in.add(new InsnNode(ICONST_0));
        in.add(new TableSwitchInsnNode(0, 9, Ld, cases));
        for (int i=0;i<cases.length;i++) {
            in.add(cases[i]);
            in.add(new JumpInsnNode(GOTO, cases[i])); // self-loop goto, artificial but bumps ratio
            in.add(new JumpInsnNode(GOTO, cases[i])); // second goto to raise goto ratio over labels
        }
        in.add(Ld);
        in.add(new InsnNode(RETURN));
        return mn;
    }

    @Test
    public void opaqueIfFalseRemovedAndWrapperDropped() {
        MethodNode mn = opaqueIfFalseWithWrapper();
        int before = mn.instructions.size();
        Normalizer.Result r = Normalizer.normalize(mn, Normalizer.Options.defaults());
        int after = r.method.instructions.size();
        assertTrue("instructions should decrease", after < before);
        assertTrue("we should have removed at least one opaque branch or wrapper",
                r.stats.opaqueBranchesRemoved > 0 || r.stats.runtimeWrappersRemoved > 0);

        // Build CFG from normalized method: should be mostly straight-line with single successor max
        ReducedCFG cfg = ReducedCFG.build(r.method);
        int maxSucc = 0;
        for (io.bytecodemapper.core.cfg.ReducedCFG.Block b : cfg.blocks()) {
            maxSucc = Math.max(maxSucc, b.succs().length);
        }
        assertTrue("CFG should not have a diamond after normalization", maxSucc <= 1);
    }

    @Test
    public void constConstCompareFolded() {
        MethodNode mn = constConstCompare();
        int before = mn.instructions.size();
        Normalizer.Result r = Normalizer.normalize(mn, Normalizer.Options.defaults());
        int after = r.method.instructions.size();
        assertTrue(after < before);

        // Build CFG: should have only fallthrough (no cond branch remains)
        ReducedCFG cfg = ReducedCFG.build(r.method);
        int condSuccBlocks = 0;
        for (io.bytecodemapper.core.cfg.ReducedCFG.Block b : cfg.blocks()) {
            if (b.succs().length > 1) condSuccBlocks++;
        }
        assertEquals(0, condSuccBlocks);
    }

    @Test
    public void constantSwitchFoldedToGoto() {
        MethodNode mn = constTableSwitch();
        Normalizer.Result r = Normalizer.normalize(mn, Normalizer.Options.defaults());
        ReducedCFG cfg = ReducedCFG.build(r.method);
        // Expect no block with 3+ succs (switch collapsed)
        int maxSucc = 0;
        for (io.bytecodemapper.core.cfg.ReducedCFG.Block b : cfg.blocks()) {
            maxSucc = Math.max(maxSucc, b.succs().length);
        }
        assertTrue("switch should be collapsed to at most 1 succ", maxSucc <= 1);
    }

    @Test
    public void flatteningDetectionSetsBypassFlag() {
        MethodNode mn = flatteningLike();
    Normalizer.Options opt = Normalizer.Options.defaults();
    // Keep dispatcher intact to test detection; no need to strip opaque branches here
    opt.normalizeOpaque = false;
    Normalizer.Result r = Normalizer.normalize(mn, opt);
        assertTrue("flattening heuristic should trigger bypass", r.bypassDFTDF);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST NormalizerTest END
