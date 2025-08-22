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

public class RefinerTest {
  private static MethodNode m(String name, String... callees){
    MethodNode mn = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, name, "()V", null, null);
    InsnList ins = mn.instructions;
    // Add deterministic set of outgoing calls
    SortedSet<String> set = new TreeSet<String>(Arrays.asList(callees));
    for (String sig : set) {
      int h = sig.indexOf('#');
      String owner = h >= 0 ? sig.substring(0, h) : sig;
      String mname = h >= 0 ? sig.substring(h + 1) : "x";
      // All methods use desc ()V for test simplicity
      ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, mname, "()V", false));
    }
    ins.add(new InsnNode(Opcodes.RETURN));
    return mn;
  }

  @Test public void refine_caps_freeze_and_logs(){
  // Build triangles via real call bags; include one JDK call to ensure exclusion
  NormalizedMethod o1 = NormalizedMethod.from("o/A", m("a", "o/B#b", "o/C#c", "java/lang/String#valueOf"));
  NormalizedMethod o2 = NormalizedMethod.from("o/B", m("b", "o/A#a", "o/C#c", "java/lang/String#valueOf"));
  NormalizedMethod o3 = NormalizedMethod.from("o/C", m("c", "o/A#a", "o/B#b", "java/lang/String#valueOf"));
  NormalizedMethod n1 = NormalizedMethod.from("n/A", m("a", "n/B#b", "n/C#c", "java/lang/String#valueOf"));
  NormalizedMethod n2 = NormalizedMethod.from("n/B", m("b", "n/A#a", "n/C#c", "java/lang/String#valueOf"));
  NormalizedMethod n3 = NormalizedMethod.from("n/C", m("c", "n/A#a", "n/B#b", "java/lang/String#valueOf"));

  CallGraphBuilder.Graph Go = CallGraphBuilder.fromMethods(Arrays.asList(o1,o2,o3), "old#");
  CallGraphBuilder.Graph Gn = CallGraphBuilder.fromMethods(Arrays.asList(n1,n2,n3), "new#");
  // Verify only intra-app edges (2 each), JDK calls excluded
  for (String u : Go.out.keySet()) assertEquals(2, Go.out.get(u).size());
  for (String v : Gn.out.keySet()) assertEquals(2, Gn.out.get(v).size());

    // Seed S0 with one strong pair and one moderate pair
    String uStrong = "old#"+o1.fingerprintSha256();
    String vStrong = "new#"+n1.fingerprintSha256();
    String uMod = "old#"+o2.fingerprintSha256();
    String vMod = "new#"+n2.fingerprintSha256();

    Map<String, Map<String, Double>> S0 = new TreeMap<String, Map<String, Double>>();
    put(S0, uStrong, vStrong, 0.85);
    put(S0, uMod, vMod, 0.50);
    // Add matching rows/cols for completeness
    put(S0, uStrong, "new#"+n2.fingerprintSha256(), 0.10);
    put(S0, uStrong, "new#"+n3.fingerprintSha256(), 0.10);
    put(S0, uMod, vStrong, 0.10);
    put(S0, uMod, "new#"+n3.fingerprintSha256(), 0.10);
    String u3 = "old#"+o3.fingerprintSha256();
    put(S0, u3, vStrong, 0.10);
    put(S0, u3, vMod, 0.10);
    put(S0, u3, "new#"+n3.fingerprintSha256(), 0.10);

    SortedMap<String, SortedMap<String, Double>> Sref = IsoRankRefiner.refine(Go, Gn, S0);

    // Validate caps and freeze
    double s0Strong = S0.get(uStrong).get(vStrong);
    double sStrong = Sref.get(uStrong).get(vStrong);
    assertTrue("freeze prevents decrease", sStrong >= s0Strong - 1e-12);

    double s0Mod = S0.get(uMod).get(vMod);
    double sMod = Sref.get(uMod).get(vMod);
    assertTrue("lift cap +0.10", sMod <= s0Mod + 0.10 + 1e-12);
    assertTrue("drop cap -0.05", sMod >= s0Mod - 0.05 - 1e-12);

    // Print top-1 refined and delta for visibility
    double top = 0.0; String topKey = null;
    for (Map.Entry<String, SortedMap<String, Double>> e : Sref.entrySet())
      for (Map.Entry<String, Double> e2 : e.getValue().entrySet())
        if (e2.getValue() > top) { top = e2.getValue(); topKey = e.getKey()+","+e2.getKey(); }
    double delta = sMod - s0Mod;
    System.out.println(String.format(java.util.Locale.ROOT, "refine.top=%.3f", top));
    System.out.println(String.format(java.util.Locale.ROOT, "refine.delta=%.3f", delta));

    assertNotNull(topKey);
  }

  private static void put(Map<String, Map<String, Double>> m, String a, String b, double v){
    Map<String, Double> r = m.get(a);
    if (r == null) { r = new TreeMap<String, Double>(); m.put(a, r); }
    r.put(b, v);
  }
}
