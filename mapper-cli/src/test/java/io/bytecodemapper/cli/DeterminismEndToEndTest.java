// >>> AUTOGEN: BYTECODEMAPPER CLI TEST DeterminismEndToEndTest BEGIN
package io.bytecodemapper.cli;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class DeterminismEndToEndTest implements Opcodes {

    @Test(timeout = 15000)
    public void mapOldNewTwice_producesIdenticalMappings() throws Exception {
        Path tmp = Files.createTempDirectory("determinism-e2e");
        Path oldJar = tmp.resolve("old.jar");
        Path newJar = tmp.resolve("new.jar");
        Path out1   = tmp.resolve("mappings1.tiny");
        Path out2   = tmp.resolve("mappings2.tiny");
        Path cache  = tmp.resolve("cache");
        Path idf    = tmp.resolve("idf.properties");

        Files.createDirectories(cache);

        // Build tiny synthetic jars
        writeJarWithClass(oldJar, "p/A", /*constVal*/ 1);
        writeJarWithClass(newJar, "p/B", /*constVal*/ 2);

        // First run
        Main.main(new String[]{
                "mapOldNew",
                "--old", oldJar.toAbsolutePath().toString(),
                "--new", newJar.toAbsolutePath().toString(),
                "--out", out1.toAbsolutePath().toString(),
                "--deterministic",
                "--cacheDir", cache.toAbsolutePath().toString(),
                "--idf", idf.toAbsolutePath().toString()
        });
        assertTrue("first mappings not written", Files.exists(out1));

        // Second run (same inputs/flags)
        Main.main(new String[]{
                "mapOldNew",
                "--old", oldJar.toAbsolutePath().toString(),
                "--new", newJar.toAbsolutePath().toString(),
                "--out", out2.toAbsolutePath().toString(),
                "--deterministic",
                "--cacheDir", cache.toAbsolutePath().toString(),
                "--idf", idf.toAbsolutePath().toString()
        });
        assertTrue("second mappings not written", Files.exists(out2));

        byte[] a = Files.readAllBytes(out1);
        byte[] b = Files.readAllBytes(out2);
        assertArrayEquals("mappings.tiny differ between identical runs", a, b);
    }

    private static void writeJarWithClass(Path jar, String internalName, int retConst) throws IOException {
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
        bos.write(cw.toByteArray(), 0, cw.toByteArray().length);
        return bos.toByteArray();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI TEST DeterminismEndToEndTest END
