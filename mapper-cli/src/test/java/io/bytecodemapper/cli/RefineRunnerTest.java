package io.bytecodemapper.cli;

import io.bytecodemapper.signals.norm.NormalizedMethod;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

import static org.junit.Assert.*;

public class RefineRunnerTest {

  private static MethodNode m(String name, String... callees){
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

  private static Map<String, Map<String, Double>> seedS0(List<NormalizedMethod> os, List<NormalizedMethod> ns) {
    SortedMap<String, Map<String, Double>> s0 = new TreeMap<String, Map<String, Double>>();
    String ou = "old#" + os.get(0).fingerprintSha256();
    String ov = "new#" + ns.get(0).fingerprintSha256();
    String ou2= "old#" + os.get(1).fingerprintSha256();
    String ov2= "new#" + ns.get(1).fingerprintSha256();
    put(s0, ou,  ov,  0.85); // strong → freeze
    put(s0, ou2, ov2, 0.50); // moderate
    // Fill rows/cols to ensure keys exist
    put(s0, ou,  "new#" + ns.get(1).fingerprintSha256(), 0.10);
    put(s0, ou,  "new#" + ns.get(2).fingerprintSha256(), 0.10);
    put(s0, ou2, ov, 0.10);
    put(s0, ou2, "new#" + ns.get(2).fingerprintSha256(), 0.10);
    String ou3 = "old#" + os.get(2).fingerprintSha256();
    put(s0, ou3, ov, 0.10);
    put(s0, ou3, ov2, 0.10);
    put(s0, ou3, "new#" + ns.get(2).fingerprintSha256(), 0.10);
    return s0;
  }

  private static void put(Map<String, Map<String, Double>> m, String a, String b, double v){
    Map<String, Double> r = m.get(a);
    if (r == null) { r = new TreeMap<String, Double>(); m.put(a, r); }
    r.put(b, v);
  }

  private static String sha256(byte[] data){
    try{
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(data);
      StringBuilder hex = new StringBuilder(d.length * 2);
      for (byte b: d) hex.append(String.format("%02x", b));
      return hex.toString();
    }catch(Exception ex){ throw new RuntimeException(ex); }
  }

  @Test public void enabled_runsRefiner_andLogs(){
    // Build two 3-cycles (triangles) with intra-app calls
    NormalizedMethod o1 = NormalizedMethod.from("o/A", m("a", "o/B#b", "o/C#c"));
    NormalizedMethod o2 = NormalizedMethod.from("o/B", m("b", "o/A#a", "o/C#c"));
    NormalizedMethod o3 = NormalizedMethod.from("o/C", m("c", "o/A#a", "o/B#b"));
    NormalizedMethod n1 = NormalizedMethod.from("n/A", m("a", "n/B#b", "n/C#c"));
    NormalizedMethod n2 = NormalizedMethod.from("n/B", m("b", "n/A#a", "n/C#c"));
    NormalizedMethod n3 = NormalizedMethod.from("n/C", m("c", "n/A#a", "n/B#b"));
    List<NormalizedMethod> os = Arrays.asList(o1,o2,o3);
    List<NormalizedMethod> ns = Arrays.asList(n1,n2,n3);
    Map<String, Map<String, Double>> S0 = seedS0(os, ns);

    // Capture stdout
    java.io.PrintStream prev = System.out;
    java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
  System.setOut(new java.io.PrintStream(bout, true));
    try {
      SortedMap<String, SortedMap<String, Double>> Sref = RefineRunner.maybeRefine(true, os, ns, S0);
      String out = new String(bout.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(out.contains("REFINE_ITER=1 delta="));
      assertTrue(out.contains("FREEZE"));
      // Caps check on the moderate pair
      String uMod = "old#"+o2.fingerprintSha256();
      String vMod = "new#"+n2.fingerprintSha256();
      double s0 = S0.get(uMod).get(vMod);
      double s1 = Sref.get(uMod).get(vMod);
      assertTrue(s1 <= s0 + 0.10 + 1e-12);
      assertTrue(s1 >= s0 - 0.05 - 1e-12);
  String hex = sha256(RefineRunner.serialize(Sref));
      System.out.println("refine.cli.sha256=" + hex);
      String out2 = new String(bout.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(out2.contains("refine.cli.sha256="));
    } finally {
      System.setOut(prev);
    }
  }

  @Test public void disabled_returnsSortedCopy_andNoLogs(){
    NormalizedMethod o1 = NormalizedMethod.from("o/A", m("a", "o/B#b", "o/C#c"));
    NormalizedMethod o2 = NormalizedMethod.from("o/B", m("b", "o/A#a", "o/C#c"));
    NormalizedMethod o3 = NormalizedMethod.from("o/C", m("c", "o/A#a", "o/B#b"));
    NormalizedMethod n1 = NormalizedMethod.from("n/A", m("a", "n/B#b", "n/C#c"));
    NormalizedMethod n2 = NormalizedMethod.from("n/B", m("b", "n/A#a", "n/C#c"));
    NormalizedMethod n3 = NormalizedMethod.from("n/C", m("c", "n/A#a", "n/B#b"));
    List<NormalizedMethod> os = Arrays.asList(o1,o2,o3);
    List<NormalizedMethod> ns = Arrays.asList(n1,n2,n3);
    Map<String, Map<String, Double>> S0 = seedS0(os, ns);

    java.io.PrintStream prev = System.out;
    java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
  System.setOut(new java.io.PrintStream(bout, true));
    try {
      SortedMap<String, SortedMap<String, Double>> Sref = RefineRunner.maybeRefine(false, os, ns, S0);
      // Equality check value-wise
      for (String k : S0.keySet()) {
        Map<String, Double> r0 = S0.get(k);
        SortedMap<String, Double> r1 = Sref.get(k);
        assertEquals(new TreeSet<String>(r0.keySet()), new TreeSet<String>(r1.keySet()));
        for (String j : r0.keySet()) assertEquals(r0.get(j), r1.get(j));
      }
      String out = new String(bout.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      assertFalse(out.contains("REFINE_ITER"));
  String hex = sha256(RefineRunner.serialize(Sref));
      System.out.println("refine.cli.sha256=" + hex);
      String out2 = new String(bout.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(out2.contains("refine.cli.sha256="));
    } finally {
      System.setOut(prev);
    }
  }
}
