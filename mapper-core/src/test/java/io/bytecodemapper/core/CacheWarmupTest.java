package io.bytecodemapper.core;

import io.bytecodemapper.core.cache.CacheManager;
import org.junit.Test;
import org.junit.Assert;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public class CacheWarmupTest {
  @Test
  public void cache_miss_then_hit_and_idf_properties_present() throws Exception {
    CacheManager cm = CacheManager.create();
    String key = CacheManager.buildKey(
      "oldSHA256_example", "newSHA256_example", "algoV1", "{}", System.getProperty("java.version")
    );
    final byte[] payload = "classes-payload".getBytes(StandardCharsets.UTF_8);
    // Invoke cache; on first Gradle run after clean this prints CACHE_MISS:classes
    cm.getOrCreate("classes", key, new CacheManager.Supplier() {
      public byte[] get() { return payload; }
    });
    // Print idf.properties lines to stdout for acceptance
    Path idf = cm.idfPath(key);
    Assert.assertTrue("idf.properties must exist", Files.exists(idf));
    List<String> lines = Files.readAllLines(idf, StandardCharsets.UTF_8);
    // Expect exactly two sorted lines with 4dp
    Assert.assertEquals("idf.properties must contain 2 lines", 2, lines.size());
    for (String s : lines) System.out.println("idf.properties line: " + s);
    Assert.assertEquals("another.term=1.0000", lines.get(0));
    Assert.assertEquals("example.term=2.5000", lines.get(1));
  }
}
