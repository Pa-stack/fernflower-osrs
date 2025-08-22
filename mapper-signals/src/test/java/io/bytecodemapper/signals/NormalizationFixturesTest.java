package io.bytecodemapper.signals;

import io.bytecodemapper.signals.fixtures.TestBytecodeFixtures;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class NormalizationFixturesTest {

    @Test
    public void buildsDeterministicFixtureJar() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        classes.put("t/Foo.class", TestBytecodeFixtures.buildGuardWrappedClass("t/Foo"));
    Path jar1 = TestBytecodeFixtures.writeDeterministicJar("norm-old.jar", classes);
    String h1 = TestBytecodeFixtures.toHex(TestBytecodeFixtures.sha256(jar1));
    long s1 = java.nio.file.Files.size(jar1);
        // Re-write same content -> same hash
    Path jar2 = TestBytecodeFixtures.writeDeterministicJar("norm-old.jar", classes);
    String h2 = TestBytecodeFixtures.toHex(TestBytecodeFixtures.sha256(jar2));
    long s2 = java.nio.file.Files.size(jar2);
    System.out.println("Fixture norm-old.jar size=" + s1 + " sha256=" + h1);
    System.out.println("Fixture norm-old.jar size=" + s2 + " sha256=" + h2);
    Assert.assertEquals("Deterministic JAR size must remain identical across runs", s1, s2);
    Assert.assertEquals("Deterministic JAR SHA-256 must remain identical across runs", h1, h2);
    }

    @Test
    public void normalization_todos_present() {
        // Placeholder assertions to be replaced in B5.
        Assert.fail("TODO: implement NormalizedMethod");
    }

    @Test
    public void buildsDeterministicMultiClassJar_withSortedEntries() throws Exception {
        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        classes.put("t/A.class", TestBytecodeFixtures.buildTrivialClass("t/A"));
        classes.put("t/B.class", TestBytecodeFixtures.buildTrivialClass("t/B"));
        Path jar1 = TestBytecodeFixtures.writeDeterministicJar("norm-multi.jar", classes);
        long s1 = java.nio.file.Files.size(jar1);
        String h1 = TestBytecodeFixtures.toHex(TestBytecodeFixtures.sha256(jar1));
        // Re-write and compare determinism
        Path jar2 = TestBytecodeFixtures.writeDeterministicJar("norm-multi.jar", classes);
        long s2 = java.nio.file.Files.size(jar2);
        String h2 = TestBytecodeFixtures.toHex(TestBytecodeFixtures.sha256(jar2));
        // Print single acceptance snippet
        System.out.println("norm-multi.jar size=" + s1 + " sha256=" + h1);
        Assert.assertEquals("multi-class JAR size determinism", s1, s2);
        Assert.assertEquals("multi-class JAR SHA determinism", h1, h2);
        // Verify entry order is lexicographically sorted
        java.util.List<String> names = new java.util.ArrayList<String>();
        try (java.util.jar.JarInputStream jin = new java.util.jar.JarInputStream(java.nio.file.Files.newInputStream(jar1))) {
            for (java.util.jar.JarEntry e; (e = jin.getNextJarEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        Assert.assertEquals(java.util.Arrays.asList("t/A.class", "t/B.class"), names);
    }
}
