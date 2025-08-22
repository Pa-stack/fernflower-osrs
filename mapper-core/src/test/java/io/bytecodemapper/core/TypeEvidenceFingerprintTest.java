package io.bytecodemapper.core;

import io.bytecodemapper.core.type.TypeEvidenceFingerprint;
import org.junit.Test;
import org.junit.Assert;

public class TypeEvidenceFingerprintTest {
  private TypeEvidenceFingerprint mkA() {
    return new TypeEvidenceFingerprint.Builder()
      .superName("x/Base")
      .addInterface("x/I1").addInterface("x/I2")
      .addFieldType("I").addFieldType("I").addFieldType("Ljava/lang/String;")
      .addMethodParamType("I").addMethodReturnType("V")
      .addMethodParamType("Ljava/lang/String;").addMethodReturnType("I")
      .methodCount(4).fieldCount(3).build();
  }
  private TypeEvidenceFingerprint mkAligned() {
    return new TypeEvidenceFingerprint.Builder()
      .superName("x/Base")
      .addInterface("x/I1").addInterface("x/I2")
      .addFieldType("I").addFieldType("I").addFieldType("Ljava/lang/String;")
      .addMethodParamType("I").addMethodReturnType("V")
      .addMethodParamType("Ljava/lang/String;").addMethodReturnType("I")
      .methodCount(4).fieldCount(3).build();
  }
  private TypeEvidenceFingerprint mkDecoy() {
    return new TypeEvidenceFingerprint.Builder()
      .superName("x/OtherBase")
      .addInterface("x/J")
      .addFieldType("J").addFieldType("Ljava/lang/Object;")
      .addMethodParamType("J").addMethodReturnType("V")
      .methodCount(2).fieldCount(2).build();
  }

  @Test public void anchoredScoreAndMargin() {
    TypeEvidenceFingerprint a = mkA();
    TypeEvidenceFingerprint aligned = mkAligned();
    TypeEvidenceFingerprint decoy = mkDecoy();
    double sAligned = a.similarity(aligned);
    double sDecoy  = a.similarity(decoy);
    double margin = sAligned - sDecoy;
    System.out.println("weights=0.40, 0.30, 0.20, 0.10");
    System.out.println(String.format(java.util.Locale.ROOT, "scores aligned=%.3f decoy=%.3f margin=%.3f", sAligned, sDecoy, margin));
    Assert.assertTrue("anchored score should be >= 0.60, was " + sAligned, sAligned >= 0.60);
    Assert.assertTrue("margin should be >= 0.05, was " + margin, margin >= 0.05);
  }

  @Test public void deterministicSerialization() {
    TypeEvidenceFingerprint a1 = new TypeEvidenceFingerprint.Builder()
      .superName("x/Base")
      .addInterface("x/I2").addInterface("x/I1")
      .addFieldType("I").addFieldType("Ljava/lang/String;").addFieldType("I")
      .addMethodParamType("Ljava/lang/String;").addMethodReturnType("I")
      .addMethodParamType("I").addMethodReturnType("V")
      .methodCount(4).fieldCount(3).build();
    TypeEvidenceFingerprint a2 = mkA();
    Assert.assertArrayEquals("toBytes() must be deterministic across insertion orders", a1.toBytes(), a2.toBytes());
  }

  @Test public void multisetJaccard_emptyVsEmpty_isOne() {
    // Both field/method multisets empty → each Jaccard = 1.0; set super/interfaces to force 0 on those terms.
    TypeEvidenceFingerprint a = new TypeEvidenceFingerprint.Builder()
      .superName("x/A") // different
      .addInterface("x/Ia") // disjoint interfaces to force sItf=0
      .methodCount(0).fieldCount(0).build();
    TypeEvidenceFingerprint b = new TypeEvidenceFingerprint.Builder()
      .superName("x/B") // different
      .addInterface("x/Ib") // disjoint interfaces to force sItf=0
      .methodCount(0).fieldCount(0).build();
    double s = a.similarity(b);
    // Expected: 0.40*1.0 (fields) + 0.30*1.0 (methods) + 0.20*0 (super) + 0.10*0 (interfaces) = 0.70
    org.junit.Assert.assertEquals("empty vs empty should yield total 0.70", 0.70, s, 1e-9);
  }

  @Test public void multisetJaccard_fieldCounts_partialOverlap_expected() {
    // Fields A: {I:2, J:1}; B: {I:1, J:3} → sFields = (1+1)/(2+3)=0.4
    // Isolate fields by making methodTypes produce 0.0 Jaccard (present on exactly one side)
    TypeEvidenceFingerprint a = new TypeEvidenceFingerprint.Builder()
      .superName("x/A")
      .addInterface("x/Ia") // disjoint interfaces to force sItf=0
      .addFieldType("I").addFieldType("I").addFieldType("J")
      .addMethodParamType("I").addMethodReturnType("V") // A has methods
      .build();
    TypeEvidenceFingerprint b = new TypeEvidenceFingerprint.Builder()
      .superName("x/B")
      .addInterface("x/Ib") // disjoint
      .addFieldType("I").addFieldType("J").addFieldType("J").addFieldType("J")
      // B has no method types → sMeths = 0
      .build();
    double s = a.similarity(b);
    double expectedTotal = 0.40 * 0.4; // fields only; others contribute 0
    System.out.println("jaccard.fields.expected=0.4000 total=" + String.format(java.util.Locale.ROOT, "%.4f", expectedTotal));
    org.junit.Assert.assertEquals("fields-only partial overlap total mismatch", expectedTotal, s, 1e-9);
  }
}
