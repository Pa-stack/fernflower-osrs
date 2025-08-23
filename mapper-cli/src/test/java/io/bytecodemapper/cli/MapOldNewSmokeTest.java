package io.bytecodemapper.cli;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

import static org.junit.Assert.*;

public class MapOldNewSmokeTest {
  private static String runCli(String... args) throws Exception {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PrintStream prevOut = System.out;
    PrintStream cap = new PrintStream(bout, true, "UTF-8");
    System.setOut(cap);
    try {
      MapOldNew.main(args);
    } finally {
      System.setOut(prevOut);
    }
    return new String(bout.toByteArray(), StandardCharsets.UTF_8);
  }

  private static String sha256(String s) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(d.length*2);
    for (byte b: d) sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
    return sb.toString();
  }

  private static boolean fixturesPresent() {
    return Files.isRegularFile(Paths.get("data/weeks/osrs-1.jar")) &&
           Files.isRegularFile(Paths.get("data/weeks/osrs-10.jar"));
  }

  @Test
  public void smoke_no_refine_deterministic() throws Exception {
    if (!fixturesPresent()) { System.out.println("fixtures not present"); }
    Assume.assumeTrue(fixturesPresent());
    String out1 = runCli("--old", "data/weeks/osrs-1.jar", "--new", "data/weeks/osrs-10.jar", "--out", "mapper-cli/build/test/no-refine-1.tiny", "--no-refine");
    String out2 = runCli("--old", "data/weeks/osrs-1.jar", "--new", "data/weeks/osrs-10.jar", "--out", "mapper-cli/build/test/no-refine-2.tiny", "--no-refine");
    assertTrue(out1.contains("pipeline.wl.k=25"));
    assertTrue(out1.contains("tau=0.60 margin=0.05"));
    assertTrue(out1.contains("assign.bytes.sha256="));
    assertFalse(out1.contains("REFINE_ITER"));
    assertEquals(sha256(out1), sha256(out2));
  }

  @Test
  public void smoke_refine_deterministic() throws Exception {
    if (!fixturesPresent()) { System.out.println("fixtures not present"); }
    Assume.assumeTrue(fixturesPresent());
    String out1 = runCli("--old", "data/weeks/osrs-1.jar", "--new", "data/weeks/osrs-10.jar", "--out", "mapper-cli/build/test/refine-1.tiny", "--refine");
    String out2 = runCli("--old", "data/weeks/osrs-1.jar", "--new", "data/weeks/osrs-10.jar", "--out", "mapper-cli/build/test/refine-2.tiny", "--refine");
    assertTrue(out1.contains("pipeline.wl.k=25"));
    assertTrue(out1.contains("REFINE_ITER=1 delta="));
    assertTrue(out1.contains("pipeline.assign.sha256="));
    assertEquals(sha256(out1), sha256(out2));
  }
}
