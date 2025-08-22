package io.bytecodemapper.signals.scoring;

import io.bytecodemapper.signals.idf.IdfStore;
import io.bytecodemapper.signals.norm.NormalizedMethod;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.security.MessageDigest;
import java.util.*;

public class CompositeScorerTest {
  private static MethodNode m(String name){
    MethodNode mn = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, name, "()V", null, null);
    InsnList ins = mn.instructions;
    ins.add(new InsnNode(Opcodes.ICONST_1));
    ins.add(new InsnNode(Opcodes.ICONST_2));
    ins.add(new InsnNode(Opcodes.IADD));
    // non-JDK call to q/C#m()V
    ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "q/C", "m", "()V", false));
    ins.add(new InsnNode(Opcodes.RETURN));
    return mn;
  }
  private static MethodNode mDistractor(String name){
    // Almost identical body to m(name), includes the same external call
    MethodNode mn = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, name, "()V", null, null);
    InsnList ins = mn.instructions;
    ins.add(new InsnNode(Opcodes.ICONST_1));
    ins.add(new InsnNode(Opcodes.ICONST_2));
    ins.add(new InsnNode(Opcodes.IADD));
    ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "q/C", "m", "()V", false));
    ins.add(new InsnNode(Opcodes.RETURN));
    return mn;
  }
  private static NormalizedMethod NM(String owner, MethodNode mn){
    return NormalizedMethod.from(owner, mn);
  }
  private static String sha256(byte[] d){
    try{ MessageDigest md=MessageDigest.getInstance("SHA-256"); byte[] x=md.digest(d); StringBuilder sb=new StringBuilder(); for(byte b:x) sb.append(String.format("%02x",b)); return sb.toString(); }catch(Exception e){ throw new RuntimeException(e); }
  }

  @Test public void weightsLiteralAndScoreAccept(){
    System.out.println("weights={calls:0.45, micro:0.25, opcode:0.15, strings:0.10}");
    NormalizedMethod a = NM("x/A", m("a"));
    NormalizedMethod b = NM("y/B", m("b"));
    IdfStore idf = IdfStore.createDefault();
    idf.put("call.q/C#m()V", 2.0000);
    double s = CompositeScorer.scoreTotal(a,b,idf, java.util.Collections.<String,String>emptyMap());
    Assert.assertTrue("composite score should accept ≥ 0.60, was "+s, s >= 0.60);
  }

  @Test public void abstainDueToLowMargin(){
  NormalizedMethod a = NM("x/A", m("a"));
  NormalizedMethod b1 = NM("y/B", m("b"));            // strong
  NormalizedMethod b2 = NM("z/B2", mDistractor("c"));  // nearly identical score, different fingerprint via owner
    IdfStore idf = IdfStore.createDefault();
    idf.put("call.q/C#m()V", 2.0000);
    List<NormalizedMethod> olds = java.util.Arrays.asList(a);
    List<NormalizedMethod> news = java.util.Arrays.asList(b1,b2);
    double s1 = CompositeScorer.scoreTotal(a,b1,idf, java.util.Collections.<String,String>emptyMap());
    double s2 = CompositeScorer.scoreTotal(a,b2,idf, java.util.Collections.<String,String>emptyMap());
    double top1 = Math.max(s1,s2), top2 = Math.min(s1,s2);
    System.out.println(String.format(java.util.Locale.ROOT, "scores top1=%.3f top2=%.3f", top1, top2));
    // Force low margin by reducing IDF weight so both rely on similar opcode/micro overlap
    IdfStore idf2 = IdfStore.createDefault(); idf2.put("call.q/C#m()V", 1.0000);
    CompositeScorer.Result r = CompositeScorer.assignPerClass(olds, news, idf2, java.util.Collections.<String,String>emptyMap());
    Assert.assertTrue("should abstain when margin < 0.05", r.matches.isEmpty());
  }

  @Test public void deterministicGreedyBytes(){
    NormalizedMethod a = NM("x/A", m("a"));
    NormalizedMethod b = NM("y/B", m("b"));
    IdfStore idf = IdfStore.createDefault();
    idf.put("call.q/C#m()V", 2.0000);
    CompositeScorer.Result r1 = CompositeScorer.assignPerClass(java.util.Arrays.asList(a), java.util.Arrays.asList(b), idf, java.util.Collections.<String,String>emptyMap());
    CompositeScorer.Result r2 = CompositeScorer.assignPerClass(java.util.Arrays.asList(a), java.util.Arrays.asList(b), idf, java.util.Collections.<String,String>emptyMap());
    String h = sha256(r1.toBytes());
    System.out.println("assign.bytes.sha256=" + h);
    Assert.assertArrayEquals(r1.toBytes(), r2.toBytes());
  }

  @Test public void ownerNormalizationImprovesCalls(){
    // old has call r/X#m()V; new has s/X#m()V; ownerMap remaps s/X -> r/X
    MethodNode mo = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "a", "()V", null, null);
    mo.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "r/X", "m", "()V", false));
    mo.instructions.add(new InsnNode(Opcodes.RETURN));
    MethodNode mn = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "b", "()V", null, null);
    mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "s/X", "m", "()V", false));
    mn.instructions.add(new InsnNode(Opcodes.RETURN));
    NormalizedMethod a = NM("a/A", mo);
    NormalizedMethod b = NM("b/B", mn);
    IdfStore idf = IdfStore.createDefault();
    idf.put("call.r/X#m()V", 2.0000);
    idf.put("call.s/X#m()V", 2.0000);
    double sNoMap = CompositeScorer.scoreTotal(a,b,idf, java.util.Collections.<String,String>emptyMap());
    java.util.Map<String,String> map = new java.util.HashMap<String,String>();
    map.put("s/X","r/X");
    double sWithMap = CompositeScorer.scoreTotal(a,b,idf, map);
    System.out.println(String.format(java.util.Locale.ROOT, "owner.norm.delta=%.4f", (sWithMap - sNoMap)));
    Assert.assertTrue("owner normalization should increase score", sWithMap > sNoMap);
  }

  @Test public void leafPenaltyApplied(){
    // A is leaf (no calls). B has one call making it non-leaf.
    MethodNode ma = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "a", "()V", null, null);
    ma.instructions.add(new InsnNode(Opcodes.ICONST_0));
    ma.instructions.add(new InsnNode(Opcodes.RETURN));
    MethodNode mbCall = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "b", "()V", null, null);
    mbCall.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "q/C", "m", "()V", false));
    mbCall.instructions.add(new InsnNode(Opcodes.RETURN));
    MethodNode mbNoCall = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "c", "()V", null, null);
    mbNoCall.instructions.add(new InsnNode(Opcodes.ICONST_0));
    mbNoCall.instructions.add(new InsnNode(Opcodes.RETURN));
    NormalizedMethod A = NM("x/A", ma);
    NormalizedMethod B_call = NM("y/B", mbCall);
    NormalizedMethod B_nocall = NM("y/B", mbNoCall);
    IdfStore idf = IdfStore.createDefault();
    idf.put("call.q/C#m()V", 2.0000);
    double penalized = CompositeScorer.scoreTotal(A,B_call,idf, java.util.Collections.<String,String>emptyMap());
    double unpenalized = CompositeScorer.scoreTotal(A,B_nocall,idf, java.util.Collections.<String,String>emptyMap());
    System.out.println(String.format(java.util.Locale.ROOT, "leaf.penalty.delta=%.4f", (unpenalized - penalized)));
    Assert.assertTrue("leaf penalty should reduce score by at least 0.05", penalized <= unpenalized - 0.05 + 1e-9);
  }

  @Ignore("TF-IDF cosine is invariant to uniform IDF scaling; both totals equal; boundary ill-defined here")
  @Test public void thresholdBoundary(){
    // Same methods; adjust IDF slightly to straddle the 0.60 cutoff
    NormalizedMethod a = NM("x/A", m("a"));
    NormalizedMethod b = NM("y/B", m("b"));
    IdfStore idfAccept = IdfStore.createDefault(); idfAccept.put("call.q/C#m()V", 2.0000);
    IdfStore idfReject = IdfStore.createDefault(); idfReject.put("call.q/C#m()V", 1.9800);
    double sAcc = CompositeScorer.scoreTotal(a,b,idfAccept, java.util.Collections.<String,String>emptyMap());
    double sRej = CompositeScorer.scoreTotal(a,b,idfReject, java.util.Collections.<String,String>emptyMap());
    System.out.println(String.format(java.util.Locale.ROOT, "boundary.accept=%.3f", sAcc));
    System.out.println(String.format(java.util.Locale.ROOT, "boundary.reject=%.3f", sRej));
    Assert.assertTrue("accepted score should be >= 0.60", sAcc >= 0.60 - 1e-9);
    Assert.assertTrue("rejected score should be < 0.60", sRej < 0.60);
  }
}
