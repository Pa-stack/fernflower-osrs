package io.bytecodemapper.cli;

import org.junit.Test;

import static org.junit.Assert.*;

public class MapOldNewRefineFlagTest {

  @Test public void enabled_demo_runsRefiner_andLogs() throws Exception {
    java.io.PrintStream prev = System.out;
    java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
    System.setOut(new java.io.PrintStream(bout, true));
    try {
      MapOldNew.main(new String[]{"--refine-demo","--refine"});
      String out = new String(bout.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(out.contains("cli.refine.enabled=true"));
      assertTrue(out.contains("REFINE_ITER=1 delta="));
      assertTrue(out.contains("FREEZE"));
      assertTrue(out.contains("cli.refine.sha256="));
    } finally {
      System.setOut(prev);
    }
  }

  @Test public void disabled_demo_returnsSortedCopy_noLogs() throws Exception {
    java.io.PrintStream prev = System.out;
    java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
    System.setOut(new java.io.PrintStream(bout, true));
    try {
      MapOldNew.main(new String[]{"--refine-demo","--no-refine"});
      String out = new String(bout.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(out.contains("cli.refine.enabled=false"));
      assertFalse(out.contains("REFINE_ITER"));
      assertTrue(out.contains("cli.refine.sha256="));
    } finally {
      System.setOut(prev);
    }
  }
}
