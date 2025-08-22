package io.bytecodemapper.signals.graph;

import io.bytecodemapper.signals.norm.NormalizedMethod;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

import static org.junit.Assert.*;

public class CallGraphBuilderTest {

  private static MethodNode mWithUniqueCallees(String name, String... callees){
    MethodNode mn = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, name, "()V", null, null);
    InsnList ins = mn.instructions;
    SortedSet<String> set = new TreeSet<String>(Arrays.asList(callees));
    for (String sig : set) {
      int h = sig.indexOf('#');
      String owner = h >= 0 ? sig.substring(0, h) : sig;
      String mname = h >= 0 ? sig.substring(h + 1) : "x";
      ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, mname, "()V", false));
    }
    ins.add(new InsnNode(Opcodes.RETURN));
    return mn;
  }

  private static MethodNode mWithDuplicateCall(String name, String targetOwner, String targetName){
    MethodNode mn = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, name, "()V", null, null);
    InsnList ins = mn.instructions;
    ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, targetOwner, targetName, "()V", false));
    ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, targetOwner, targetName, "()V", false));
    ins.add(new InsnNode(Opcodes.RETURN));
    return mn;
  }

  @Test public void missingCallee_noEdgeCreated(){
    // A calls B and Missing.x; only A→B edge should exist
    NormalizedMethod a = NormalizedMethod.from("o/A", mWithUniqueCallees("a", "o/B#b", "o/Missing#x"));
    NormalizedMethod b = NormalizedMethod.from("o/B", mWithUniqueCallees("b"));
    CallGraphBuilder.Graph g = CallGraphBuilder.fromMethods(Arrays.asList(a,b), "old#");
    String idA = "old#" + a.fingerprintSha256();
    String idB = "old#" + b.fingerprintSha256();
    assertTrue(g.out.containsKey(idA));
    assertTrue(g.out.containsKey(idB));
    SortedSet<String> outsA = g.out.get(idA);
    assertEquals(1, outsA.size());
    assertTrue(outsA.contains(idB));
    assertTrue(g.out.get(idB).isEmpty());
  }

  @Test public void duplicateCalls_singleEdge(){
    // A calls B twice; edge set must still contain only one A→B edge
    NormalizedMethod a = NormalizedMethod.from("o/A", mWithDuplicateCall("a", "o/B", "b"));
    NormalizedMethod b = NormalizedMethod.from("o/B", mWithUniqueCallees("b"));
    CallGraphBuilder.Graph g = CallGraphBuilder.fromMethods(Arrays.asList(a,b), "old#");
    String idA = "old#" + a.fingerprintSha256();
    String idB = "old#" + b.fingerprintSha256();
    SortedSet<String> outsA = g.out.get(idA);
    assertEquals(1, outsA.size());
    assertTrue(outsA.contains(idB));
  }
}
