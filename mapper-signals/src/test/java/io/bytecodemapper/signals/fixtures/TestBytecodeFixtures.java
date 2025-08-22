package io.bytecodemapper.signals.fixtures;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Deterministic ASM bytecode fixture builder for tests.
 * Writes stable JARs under build/test-fixtures with fixed timestamps and sorted entries.
 */
public final class TestBytecodeFixtures {
    private TestBytecodeFixtures() {}

    public static Path fixturesDir() {
        return Paths.get("build", "test-fixtures");
    }

    /**
     * Write a deterministic JAR with the given class entries. The entries are sorted by name and
     * each JarEntry timestamp is set to 0 for reproducibility.
     */
    public static Path writeDeterministicJar(String jarName, Map<String, byte[]> classes) throws Exception {
        Files.createDirectories(fixturesDir());
        Path out = fixturesDir().resolve(jarName);
        try (OutputStream fos = Files.newOutputStream(out); JarOutputStream jos = new JarOutputStream(fos)) {
            // No manifest to keep entries pure class files
            List<String> names = new ArrayList<String>(classes.keySet());
            Collections.sort(names);
            for (String name : names) {
                JarEntry e = new JarEntry(name);
                e.setTime(0L); // fixed epoch for stability
                jos.putNextEntry(e);
                jos.write(classes.get(name));
                jos.closeEntry();
            }
        }
        return out;
    }

    public static byte[] sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        return md.digest();
    }

    public static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    /**
     * Build a trivial class with an early-return guard and a try/catch wrapper for later normalization tests.
     */
    public static byte[] buildGuardWrappedClass(String internalName) {
        ClassNode cn = new ClassNode(Opcodes.ASM7);
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = internalName;
        cn.superName = "java/lang/Object";

        // <init>()V
        MethodNode init = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        InsnList il = init.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        // public static int f(int p)
        MethodNode m = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "f", "(I)I", null, null);
        m.tryCatchBlocks = new ArrayList<TryCatchBlockNode>();
        LabelNode L0 = new LabelNode();
        LabelNode L1 = new LabelNode();
        LabelNode L2 = new LabelNode();
        m.instructions.add(L0);
        // Opaque guard: if (p == 42) return 0;
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        m.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 42));
        m.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPNE, L1));
        m.instructions.add(new InsnNode(Opcodes.ICONST_0));
        m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m.instructions.add(L1);
        // try { return p; } catch (RuntimeException e) { throw e; }
        m.tryCatchBlocks.add(new TryCatchBlockNode(L1, L2, L2, "java/lang/RuntimeException"));
        m.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        m.instructions.add(new InsnNode(Opcodes.IRETURN));
        m.instructions.add(L2);
        m.instructions.add(new InsnNode(Opcodes.ATHROW));
        cn.methods.add(m);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /** Build a second trivial class for multi-class JAR order tests. */
    public static byte[] buildTrivialClass(String internalName) {
        ClassNode cn = new ClassNode(Opcodes.ASM7);
        cn.version = Opcodes.V1_8;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = internalName;
        cn.superName = "java/lang/Object";

        MethodNode init = new MethodNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
