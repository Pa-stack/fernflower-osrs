package io.bytecodemapper.signals.idf;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class IdfStoreTest {
  @Test public void persistDeterministicAndClamp() throws Exception {
    IdfStore s = new IdfStore(0.9);
    // emulate a week of observations via direct updates
    int N = 2000;
    s.update("call.foo", N, 1000);
    s.update("call.bar", N, 0);
    s.update("str.hello", N, 50);

    double a = s.get("call.foo", 1.0);
    double b = s.get("call.bar", 1.0);
    assertTrue("clamp upper", a <= 3.0);
    assertTrue("clamp lower", b >= 0.5);

    Path f = new java.io.File("build/test-idf.properties").toPath();
    s.save(f);
    byte[] bytes1 = Files.readAllBytes(f);
    byte[] bytes2 = Files.readAllBytes(f);
    assertEquals(new String(bytes1, StandardCharsets.UTF_8), new String(bytes2, StandardCharsets.UTF_8));

    IdfStore r = new IdfStore(0.9);
    r.load(f);
    assertEquals(round4(a), round4(r.get("call.foo", 1.0)), 0.0);
    assertEquals(round4(b), round4(r.get("call.bar", 1.0)), 0.0);

    // EMA clamp-above: start at 2.99, apply updates with fresh≈1.6931; never exceed 3.0
    r.put("call.high", 2.99);
    for (int i = 0; i < 50; i++) r.update("call.high", 1, 0);
    assertTrue("upper bound clamp via EMA", r.get("call.high", 0) <= 3.0);

    // EMA clamp-below: start near 0.5 and update with fresh≈1.0; never go below 0.5
    r.put("str.low", 0.51);
    for (int i = 0; i < 50; i++) r.update("str.low", 1_000_000, 1_000_000);
    assertTrue("lower bound clamp via EMA", r.get("str.low", 1.0) >= 0.5);

    // Sorted save order even with unsorted inserts
    r.put("zzz.key", 1.1111);
    r.put("aaa.key", 2.2222);
    r.put("call.owner#name(desc)", 1.2345);
    r.put("str.token", 0.5);
    r.save(f);
    java.util.List<String> lines = java.nio.file.Files.readAllLines(f, StandardCharsets.UTF_8);
    java.util.List<String> sorted = new java.util.ArrayList<String>(lines);
    java.util.Collections.sort(sorted);
    assertEquals("file must be saved in sorted key order", sorted, lines);

    // Re-save and check byte-identical
    byte[] before = java.nio.file.Files.readAllBytes(f);
    r.save(f);
    byte[] after = java.nio.file.Files.readAllBytes(f);
    assertArrayEquals(before, after);

    // Print acceptance lines
    for (String line : lines) {
      if (line.startsWith("call.owner#name(desc)=")) System.out.println("idf.properties line: " + line);
      if (line.startsWith("str.token=")) System.out.println("idf.properties line: " + line);
    }
  }

  private static double round4(double v){
    return Math.round(v*10000.0)/10000.0;
  }
}
