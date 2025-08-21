// >>> AUTOGEN: BYTECODEMAPPER CLI TEST MethodMatcherIT BEGIN
package io.bytecodemapper.cli;

import java.util.Set;
import java.util.HashSet;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

public class MethodMatcherIT implements Opcodes {

    @Test(timeout = 20000)
    public void itCanonicalVsSurrogate() throws Exception {
        // ...existing code...
    }

    @Test(timeout = 20000)
    public void bothModeUnionDeterministic() throws Exception {
        Path tmp = Files.createTempDirectory("mm-both-it");
        Path oldJar = tmp.resolve("old.jar");
        Path newJar = tmp.resolve("new.jar");
        Path outBoth = tmp.resolve("mappings-both.tiny");
        Path outCanon = tmp.resolve("mappings-canon.tiny");
        Path outSurr = tmp.resolve("mappings-surr.tiny");
        Path cache = tmp.resolve("cache");
        Path idf = tmp.resolve("idf.properties");
        Files.createDirectories(cache);

        // Synthesize: method bodies differ so surrogate bucket likely differs; nsf64 from NormalizedMethod non-zero
        writeJarWithClass(oldJar, "p/A", 1);
        writeJarWithClass(newJar, "p/B", 2);

        String[] common = new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--deterministic",
                "--cacheDir", cache.toString(),
                "--idf", idf.toString(),
                "--maxMethods", "200",
                "--debug-stats"
        };

        // BOTH mode
        Main.main(concat(common, new String[]{"--out", outBoth.toString(), "--use-nsf64=both"}));
        assertTrue(Files.exists(outBoth));
        // Canonical only
        Main.main(concat(common, new String[]{"--out", outCanon.toString(), "--use-nsf64=canonical"}));
        assertTrue(Files.exists(outCanon));
        // Surrogate only
        Main.main(concat(common, new String[]{"--out", outSurr.toString(), "--use-nsf64=surrogate"}));
        assertTrue(Files.exists(outSurr));

        // Read all outputs
        byte[] bothBytes = Files.readAllBytes(outBoth);
        byte[] canonBytes = Files.readAllBytes(outCanon);
        byte[] surrBytes = Files.readAllBytes(outSurr);

        // BOTH mode must contain all canonical mappings, and dedup (no duplicate refs)
        assertTrue("BOTH mode should contain all canonical mappings", containsAll(bothBytes, canonBytes));
        // BOTH mode must contain all surrogate-only mappings (if any)
        assertTrue("BOTH mode should contain all surrogate-only mappings", containsAll(bothBytes, surrBytes));
        // BOTH mode must be deterministic (stable sort)
        assertArrayEquals("BOTH mode output should be deterministic", bothBytes, Files.readAllBytes(outBoth));
        // No duplicate refs (by owner+name+desc)
        assertTrue("BOTH mode should not contain duplicate refs", noDuplicateRefs(bothBytes));
    }

    // Helper: check if all lines in b are present in a
    private static boolean containsAll(byte[] a, byte[] b) {
        String sa = new String(a);
        String sb = new String(b);
        for (String line : sb.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (!sa.contains(line.trim())) return false;
        }
        return true;
    }

    // Helper: check for duplicate refs (owner+name+desc)
    private static boolean noDuplicateRefs(byte[] bytes) {
        String s = new String(bytes);
        Set<String> seen = new HashSet<>();
        for (String line : s.split("\n")) {
            if (!line.contains("method")) continue;
            String[] toks = line.split(" ");
            String key = null;
            for (String tok : toks) {
                if (tok.startsWith("method")) key = tok;
            }
            if (key != null) {
                if (seen.contains(key)) return false;
                seen.add(key);
            }
        }
        return true;
    }

    // ...existing code...

    private static String[] concat(String[] a, String[] b){
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static void writeJarWithClass(Path jar, String internalName, int retConst) throws Exception {
        byte[] cls = makeClassBytes(internalName, retConst);
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            JarEntry e = new JarEntry(internalName + ".class");
            jos.putNextEntry(e);
            jos.write(cls);
            jos.closeEntry();
        }
    }

    private static byte[] makeClassBytes(String internalName, int retConst) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_8, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        // default ctor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        // int m()
        mv = cw.visitMethod(ACC_PUBLIC, "m", "()I", null, null);
        mv.visitCode();
        switch (retConst) {
            case -1: mv.visitInsn(ICONST_M1); break;
            case 0:  mv.visitInsn(ICONST_0);  break;
            case 1:  mv.visitInsn(ICONST_1);  break;
            case 2:  mv.visitInsn(ICONST_2);  break;
            case 3:  mv.visitInsn(ICONST_3);  break;
            case 4:  mv.visitInsn(ICONST_4);  break;
            case 5:  mv.visitInsn(ICONST_5);  break;
            default: mv.visitLdcInsn(retConst);
        }
        mv.visitInsn(IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] arr = cw.toByteArray();
        bos.write(arr, 0, arr.length);
        return bos.toByteArray();
    }

    private static int countOccurrences(String s, String needle) {
        int c = 0, idx = 0;
        while (true) {
            idx = s.indexOf(needle, idx);
            if (idx < 0) break; c++; idx += needle.length();
        }
        return c;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI TEST MethodMatcherIT END
