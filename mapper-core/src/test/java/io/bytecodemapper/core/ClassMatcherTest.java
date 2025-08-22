package io.bytecodemapper.core;

import io.bytecodemapper.core.type.ClassMatcher;
import io.bytecodemapper.core.type.TypeEvidenceFingerprint;
import org.junit.Test;
import org.junit.Assert;
import java.util.*;
import java.security.MessageDigest;

public class ClassMatcherTest {
  private static String hex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length*2);
    for (byte x: b) sb.append(String.format("%02x", x));
    return sb.toString();
  }
  private static byte[] sha256(byte[] data) {
    try { return MessageDigest.getInstance("SHA-256").digest(data); }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  private TypeEvidenceFingerprint fpA() {
    return new TypeEvidenceFingerprint.Builder()
      .superName("x/Base").addInterface("x/I1").addInterface("x/I2")
      .addFieldType("I").addFieldType("I")
      .addMethodParamType("I").addMethodReturnType("V")
      .methodCount(2).fieldCount(2).build();
  }
  private TypeEvidenceFingerprint fpA_same() { return fpA(); }
  private TypeEvidenceFingerprint fpNear() {
    return new TypeEvidenceFingerprint.Builder()
      .superName("x/Base").addInterface("x/I1")
      .addFieldType("I").addFieldType("I")
      .addMethodParamType("I").addMethodReturnType("V")
      .methodCount(2).fieldCount(2).build();
  }
  private TypeEvidenceFingerprint fpDecoy() {
    return new TypeEvidenceFingerprint.Builder()
      .superName("x/Other").addInterface("x/J")
      .addFieldType("J").addMethodParamType("J").addMethodReturnType("V")
      .methodCount(1).fieldCount(1).build();
  }

  @Test public void anchorsGreedyMatch() {
    Map<String, TypeEvidenceFingerprint> oldMap = new HashMap<String, TypeEvidenceFingerprint>();
    Map<String, TypeEvidenceFingerprint> newMap = new HashMap<String, TypeEvidenceFingerprint>();
    oldMap.put("o/A", fpA());
    oldMap.put("o/X", fpDecoy());
    newMap.put("n/B", fpA_same()); // anchor for o/A
    newMap.put("n/Y", fpNear());
    ClassMatcher m = new ClassMatcher();
    ClassMatcher.Result r = m.match(oldMap, newMap);
    Assert.assertEquals("anchor should match 1:1", "n/B", r.matches.get("o/A"));
  Assert.assertEquals("anchor score should be exactly 1.0", 1.0, r.scores.get("o/A"), 0.0);
  }

  @Test public void abstainOnLowMargin() {
    // Two identical best candidates give margin 0 -> abstain even though score >= TAU
    TypeEvidenceFingerprint shape = new TypeEvidenceFingerprint.Builder()
      .superName("x/S").addInterface("x/I1")
      .addFieldType("I").addMethodParamType("I").addMethodReturnType("V")
      .methodCount(1).fieldCount(1).build();
    Map<String, TypeEvidenceFingerprint> oldMap = new HashMap<String, TypeEvidenceFingerprint>();
    Map<String, TypeEvidenceFingerprint> newMap = new HashMap<String, TypeEvidenceFingerprint>();
    // Make old slightly different to avoid anchor: extra interface
    oldMap.put("o/A", new TypeEvidenceFingerprint.Builder()
      .superName("x/S").addInterface("x/I1").addInterface("x/I2")
      .addFieldType("I").addMethodParamType("I").addMethodReturnType("V")
      .methodCount(1).fieldCount(1).build());
    newMap.put("n/C1", shape);
    newMap.put("n/C2", shape);
    ClassMatcher m = new ClassMatcher();
    ClassMatcher.Result r = m.match(oldMap, newMap);
    Assert.assertNull("should abstain when margin < 0.05 even if top1 >= 0.60", r.matches.get("o/A"));
  }

  @Test public void deterministicOrderingAndBytesStable() {
    Map<String, TypeEvidenceFingerprint> oldMap = new HashMap<String, TypeEvidenceFingerprint>();
    Map<String, TypeEvidenceFingerprint> newMap = new HashMap<String, TypeEvidenceFingerprint>();
    TypeEvidenceFingerprint a = new TypeEvidenceFingerprint.Builder()
      .superName("x/S").addFieldType("I").addMethodParamType("I").addMethodReturnType("V").build();
    TypeEvidenceFingerprint b = new TypeEvidenceFingerprint.Builder()
      .superName("x/S").addFieldType("I").addMethodParamType("I").addMethodReturnType("V").build();
    oldMap.put("o/A", a);
    newMap.put("n/A", b);
    ClassMatcher m = new ClassMatcher();
    ClassMatcher.Result r1 = m.match(oldMap, newMap);
    ClassMatcher.Result r2 = m.match(oldMap, newMap);
    byte[] b1 = r1.toBytes(), b2 = r2.toBytes();
    String h = hex(sha256(b1));
    System.out.println("tau=0.60 margin=0.05");
    System.out.println("classmatch.bytes.sha256=" + h);
    Assert.assertArrayEquals("result bytes must be identical across runs", b1, b2);
  }
}
