package io.bytecodemapper.core.wl;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.dom.Dominators;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class WLRefinementTest implements Opcodes {
    @org.junit.BeforeClass
    public static void caps() {
        System.setProperty("mapper.wl.max.blocks", System.getProperty("mapper.wl.max.blocks", "400"));
        System.setProperty("mapper.wl.cache.size", System.getProperty("mapper.wl.cache.size", "4096"));
        System.setProperty("mapper.wl.watchdog.ms", System.getProperty("mapper.wl.watchdog.ms", "2000"));
    }
    private static MethodNode singleBlock(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "a", "()V", null, null); InsnList il=m.instructions; il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(ICONST_2)); il.add(new InsnNode(IADD)); il.add(new InsnNode(RETURN)); return m; }
    private static MethodNode loopSimpleNoFold(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "b", "()V", null, null); InsnList il=m.instructions; LabelNode L0=new LabelNode(); LabelNode L1=new LabelNode(); il.add(new InsnNode(ICONST_3)); il.add(L0); il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(ISUB)); il.add(new JumpInsnNode(IFGT, L0)); il.add(L1); il.add(new InsnNode(RETURN)); m.tryCatchBlocks=new java.util.ArrayList<TryCatchBlockNode>(); return m; }
    private static MethodNode tableSwitchNoFold(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "c", "()V", null, null); InsnList il=m.instructions; LabelNode D=new LabelNode(); LabelNode A=new LabelNode(); LabelNode B=new LabelNode(); il.add(new InsnNode(ICONST_1)); java.util.List<LabelNode> labels=new java.util.ArrayList<LabelNode>(); labels.add(A); labels.add(B); il.add(new TableSwitchInsnNode(0,1, D, labels.toArray(new LabelNode[0]))); il.add(A); il.add(new InsnNode(RETURN)); il.add(B); il.add(new InsnNode(RETURN)); il.add(D); il.add(new InsnNode(RETURN)); return m; }

    private static String sigHex(MethodNode mn,int rounds){ return WLRefinement.sha256Hex(WLRefinement.signature(mn, rounds)); }

    @Test(timeout=60000) public void wlRoundtripAndSnippets(){ System.out.println("wl.test.start"); int R=2; System.out.println("wl.rounds="+R); MethodNode straight=singleBlock(); MethodNode loop=loopSimpleNoFold(); MethodNode sw=tableSwitchNoFold(); String hStraight=sigHex(straight,R), hLoop=sigHex(loop,R), hSwitch=sigHex(sw,R); System.out.println("wl.sig.sha256="+hStraight); System.out.println("wl.sig.sha256="+hLoop); System.out.println("wl.sig.sha256="+hSwitch);
        ReducedCFG gLoop=ReducedCFG.build(loop); Dominators dLoop=Dominators.compute(gLoop); boolean hasBack=false; for(ReducedCFG.Block b: gLoop.blocks()){ for(int p: b.preds()) if(dLoop.dominates(b.id,p)){ hasBack=true; break;} if(hasBack) break; } Assert.assertTrue(hasBack);
        ReducedCFG gSw=ReducedCFG.build(sw); int maxOut=0; for(ReducedCFG.Block b: gSw.blocks()) maxOut=Math.max(maxOut, b.succs().length); Assert.assertTrue(maxOut>=2);
        Assert.assertNotEquals(hStraight,hLoop); Assert.assertNotEquals(hStraight,hSwitch); }
}
