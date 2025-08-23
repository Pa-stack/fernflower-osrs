package io.bytecodemapper.core.wl;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class MethodCandidateGeneratorTest implements Opcodes {
    private static MethodNode straight(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "s","()V",null,null); InsnList il=m.instructions; il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(RETURN)); return m; }
    private static MethodNode loop(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "l","()V",null,null); InsnList il=m.instructions; LabelNode L=new LabelNode(); il.add(new InsnNode(ICONST_2)); il.add(L); il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(ISUB)); il.add(new JumpInsnNode(IFGT, L)); il.add(new InsnNode(RETURN)); return m; }

    // local stub with fingerprintSha256
    private static final class Key { private final String id; Key(String id){this.id=id;} public String fingerprintSha256(){ return id; } public String toString(){ return id; } }

    @Test public void rankLoopOverStraight(){ int K=MethodCandidateGenerator.DEFAULT_K; System.out.println("wl.topk.k="+K); MethodNode old=loop(); MethodNode new1=loop(); MethodNode new2=straight(); Key o=new Key("oldX"), k1=new Key("newLoop"), k2=new Key("newStraight"); java.util.Map<Object,MethodNode> nodes=new java.util.TreeMap<Object,MethodNode>(new java.util.Comparator<Object>(){ public int compare(Object a,Object b){ return a.toString().compareTo(b.toString()); }}); nodes.put(o,old); nodes.put(k1,new1); nodes.put(k2,new2); java.util.List<Object> newKeys=java.util.Arrays.asList(k1,k2); java.util.List<MethodCandidateGenerator.Candidate> cs=MethodCandidateGenerator.candidatesFor(o,newKeys,K,nodes);
        StringBuilder sb=new StringBuilder(); for(MethodCandidateGenerator.Candidate c: cs){ sb.append(c.newId).append(':').append(String.format(java.util.Locale.ROOT,"%.6f", c.wlScore)).append('\n'); } String hex=WLRefinement.sha256Hex(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); System.out.println("wl.candidates.sha256="+hex);
        Assert.assertTrue(cs.size()>=2); Assert.assertTrue(cs.get(0).newId.startsWith("new#newLoop")); for(int i=1;i<cs.size();i++) Assert.assertTrue(cs.get(i-1).wlScore>=cs.get(i).wlScore); }
}
