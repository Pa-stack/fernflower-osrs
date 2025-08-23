package io.bytecodemapper.cli;

import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

public class MapOldNewSmokeTest {

  private static class RunResult { final String out; final int exit; RunResult(String o,int e){out=o;exit=e;} }

  private static RunResult runCli(String... args) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream origOut = System.out;
    int exit = 0;
    try {
      System.setOut(new PrintStream(baos, true, "UTF-8"));
      io.bytecodemapper.cli.MapOldNew.main(args);
    } catch (Throwable t) {
      exit = 1;
    } finally {
      System.setOut(origOut);
    }
    return new RunResult(new String(baos.toByteArray(), StandardCharsets.UTF_8), exit);
  }

  private static boolean fixturesAvailable(Path oldJar, Path newJar){
    return Files.isRegularFile(oldJar) && Files.isRegularFile(newJar);
  }

  @Test
  public void anchors_noRefine_deterministic() throws Exception {
    Path oldJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("data/weeks/2025-34/old.jar");
    Path newJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("data/weeks/2025-34/new.jar");
    if (!fixturesAvailable(oldJar, newJar)) {
      System.out.println("[SMOKE] fixtures not present; skipping");
      throw new org.junit.internal.AssumptionViolatedException("fixtures not present");
    }
    Path outTiny = io.bytecodemapper.cli.util.CliPaths.resolveOutput("mapper-cli/build/test-out-noref.tiny");
    Files.createDirectories(outTiny.getParent());

    String[] base = new String[]{
        "--old", oldJar.toString(),
        "--new", newJar.toString(),
        "--out", outTiny.toString(),
        "--no-refine",
        "--maxMethods", "5"
    };
    RunResult r1 = runCli(base);
    RunResult r2 = runCli(base);
    assertEquals(0, r1.exit);
    assertEquals(0, r2.exit);
    assertTrue(r1.out.contains("pipeline.wl.k=25"));
    assertTrue(r1.out.contains("tau=0.60 margin=0.05"));
    assertTrue(r1.out.contains("assign.bytes.sha256="));
    assertFalse(r1.out.contains("REFINE_ITER"));
    assertEquals(hash(r1.out), hash(r2.out));
  }

  @Test
  public void anchors_refine_deterministic() throws Exception {
    Path oldJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("data/weeks/2025-34/old.jar");
    Path newJar = io.bytecodemapper.cli.util.CliPaths.resolveInput("data/weeks/2025-34/new.jar");
    if (!fixturesAvailable(oldJar, newJar)) {
      System.out.println("[SMOKE] fixtures not present; skipping");
      throw new org.junit.internal.AssumptionViolatedException("fixtures not present");
    }
    Path outTiny = io.bytecodemapper.cli.util.CliPaths.resolveOutput("mapper-cli/build/test-out-refine.tiny");
    Files.createDirectories(outTiny.getParent());

    String[] base = new String[]{
        "--old", oldJar.toString(),
        "--new", newJar.toString(),
        "--out", outTiny.toString(),
        "--refine",
        "--maxMethods", "5"
    };
    RunResult r1 = runCli(base);
    RunResult r2 = runCli(base);
    assertEquals(0, r1.exit);
    assertEquals(0, r2.exit);
    assertTrue(r1.out.contains("pipeline.wl.k=25"));
    assertTrue(r1.out.contains("REFINE_ITER="));
    assertTrue(r1.out.contains("pipeline.assign.sha256="));
    assertEquals(hash(r1.out), hash(r2.out));
  }

  private static String hash(String s) throws Exception {
    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
    byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(d.length * 2);
    for (byte b : d) sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
    return sb.toString();
  }
}
// End of test
