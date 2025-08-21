// >>> AUTOGEN: BYTECODEMAPPER CLI TEST FlatteningWideningIT BEGIN
package io.bytecodemapper.cli;

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

public class FlatteningWideningIT {
    @Test(timeout = 30000)
    public void wideningUnderFlattening_emitsReportFields() throws Exception {
        Path tmp = Files.createTempDirectory("mm-phase4-accept-it");
        Path oldJar = tmp.resolve("old.jar");
        Path newJar = tmp.resolve("new.jar");
        Path outTiny = tmp.resolve("out.tiny");
        Path report = tmp.resolve("report.json");
        Files.createDirectories(tmp);

    // Synthesize trivial jars
    writeJarWithClass(oldJar, "q/C", 1);
    writeJarWithClass(newJar, "q/C", 2);

        Main.main(new String[]{
                "mapOldNew",
                "--old", oldJar.toString(),
                "--new", newJar.toString(),
                "--out", outTiny.toString(),
                "--deterministic",
                "--report", report.toString(),
                "--nsf-near", "2",
                "--stack-cos", "0.60"
        });

        assertTrue(Files.exists(outTiny));
        assertTrue(Files.exists(report));
        String js = new String(Files.readAllBytes(report), "UTF-8");
        assertTrue(js.contains("\"flattening_detected\""));
        assertTrue(js.contains("\"near_before_gates\""));
        assertTrue(js.contains("\"near_after_gates\""));
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

    private static byte[] makeClassBytes(String internalName, int retConst) throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()I", null, null);
        mv.visitCode();
        switch (retConst) {
            case -1: mv.visitInsn(Opcodes.ICONST_M1); break;
            case 0:  mv.visitInsn(Opcodes.ICONST_0);  break;
            case 1:  mv.visitInsn(Opcodes.ICONST_1);  break;
            case 2:  mv.visitInsn(Opcodes.ICONST_2);  break;
            case 3:  mv.visitInsn(Opcodes.ICONST_3);  break;
            case 4:  mv.visitInsn(Opcodes.ICONST_4);  break;
            case 5:  mv.visitInsn(Opcodes.ICONST_5);  break;
            default: mv.visitLdcInsn(Integer.valueOf(retConst));
        }
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] arr = cw.toByteArray();
        bos.write(arr, 0, arr.length);
        return bos.toByteArray();
    }
}
// >>> AUTOGEN: BYTECODEMAPPER CLI TEST FlatteningWideningIT END
