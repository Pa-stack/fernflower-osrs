// >>> AUTOGEN: BYTECODEMAPPER IO RemapService BEGIN
package io.bytecodemapper.io.remap;

import io.bytecodemapper.io.tiny.TinyV2Mappings;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/** Remapping service with TinyRemapper default and ASM fallback; Java 8 compatible. */
public final class RemapService {

    public enum RemapperKind { TINY, ASM }
    public enum MappingFormat { TINY2, ENIGMA }

    public static final class VerifyStats {
        public int classes; public int methods; public int fields;
        @Override public String toString() {
            return "classes=" + classes + " methods=" + methods + " fields=" + fields;
        }
    }

    private RemapService() {}

    public static VerifyStats applyMappings(File inJar, File mappingFile, File outJar,
                                            MappingFormat fmt, RemapperKind kind,
                                            boolean verify, boolean deterministic) throws IOException {
        if (inJar == null || mappingFile == null || outJar == null) throw new NullPointerException();
        if (kind == RemapperKind.TINY) {
            return applyTinyRemapper(inJar.toPath(), mappingFile.toPath(), outJar.toPath(), fmt, verify, deterministic);
        } else {
            return applyAsmFallback(inJar.toPath(), mappingFile.toPath(), outJar.toPath(), fmt, verify, deterministic);
        }
    }

    // ---- TinyRemapper path ----
    private static VerifyStats applyTinyRemapper(Path inJar, Path mappings, Path outJar,
                                                 MappingFormat fmt, boolean verify, boolean deterministic) throws IOException {
        if (fmt == MappingFormat.ENIGMA) {
            throw new IOException("ENIGMA format not supported in this build; use --format=tiny2");
        }
        final TinyV2Mappings t = TinyV2Mappings.read(mappings);
        final VerifyStats stats = new VerifyStats();
        stats.classes = t.classMap.size();
        stats.methods = t.methodMap.size();
        stats.fields = t.fieldMap.size();
        final IMappingProvider provider = new IMappingProvider() {
            @Override public void load(MappingAcceptor acceptor) {
                for (Map.Entry<String,String> ce : t.classMap.entrySet()) {
                    acceptor.acceptClass(ce.getKey(), ce.getValue());
                }
                for (Map.Entry<String,String> me : t.methodMap.entrySet()) {
                    String key = me.getKey();
                    int p = key.indexOf('#'); int q = key.indexOf('(', p+1);
                    String owner = key.substring(0, p);
                    String name = key.substring(p+1, q);
                    String desc = key.substring(q);
                    acceptor.acceptMethod(new IMappingProvider.Member(owner, name, desc), me.getValue());
                }
                for (Map.Entry<String,String> fe : t.fieldMap.entrySet()) {
                    String key = fe.getKey();
                    int p = key.indexOf('#'); int q = key.indexOf(':', p+1);
                    String owner = key.substring(0, p);
                    String name = key.substring(p+1, q);
                    String desc = key.substring(q+1);
                    acceptor.acceptField(new IMappingProvider.Member(owner, name, desc), fe.getValue());
                }
            }
        };

        Files.createDirectories(outJar.getParent());
        TinyRemapper tr = TinyRemapper.newRemapper()
                .withMappings(provider)
                .fixPackageAccess(true)
                .renameInvalidLocals(false)
                .rebuildSourceFilenames(false)
                .build();

        try (OutputConsumerPath out = new OutputConsumerPath.Builder(outJar).build()) {
            out.addNonClassFiles(inJar, NonClassCopyMode.FIX_META_INF, tr);
            tr.readInputs(inJar);
            tr.apply(out);
        } finally {
            tr.finish();
        }

        if (deterministic) repackSorted(outJar);
        if (verify) System.out.println("[applyMappings] TinyRemapper verify: " + stats);
        return stats;
    }

    // ---- ASM fallback path ----
    private static VerifyStats applyAsmFallback(Path inJar, Path mappings, Path outJar,
                                                MappingFormat fmt, boolean verify, boolean deterministic) throws IOException {
        if (fmt == MappingFormat.ENIGMA) {
            throw new IOException("ENIGMA format not supported in this build; use --format=tiny2");
        }
        TinyV2Mappings t = TinyV2Mappings.read(mappings);

        Files.createDirectories(outJar.getParent());
        AsmJarRemapper.remapJar(inJar, outJar, t);
        if (deterministic) repackSorted(outJar);
        if (verify) System.out.println("[applyMappings] ASM verify: " + countStatsFromTiny(t));
        return countStatsFromTiny(t);
    }

    private static VerifyStats countStatsFromTiny(io.bytecodemapper.io.tiny.TinyV2Mappings t) {
        VerifyStats s = new VerifyStats();
        s.classes = t.classMap.size();
        s.methods = t.methodMap.size();
        s.fields = t.fieldMap.size();
        return s;
    }

    // >>> AUTOGEN: BYTECODEMAPPER IO repackSorted HARDEN BEGIN
    private static void repackSorted(Path jarPath) throws IOException {
        Path tmp = Files.createTempFile(jarPath.getParent(), "repack-", ".jar");
        List<String> names = new ArrayList<String>();
        Map<String, byte[]> blobs = new HashMap<String, byte[]>();

        JarInputStream jis = new JarInputStream(Files.newInputStream(jarPath));
        try {
            JarEntry e;
            byte[] buf = new byte[8192];
            while ((e = jis.getNextJarEntry()) != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int n;
                while ((n = jis.read(buf)) > 0) bos.write(buf, 0, n);
                blobs.put(e.getName(), bos.toByteArray());
                names.add(e.getName());
            }
        } finally {
            jis.close();
        }

        // Ensure MANIFEST (and META-INF dir) first if present
        List<String> ordered = new ArrayList<String>();
        if (names.contains("META-INF/")) ordered.add("META-INF/");
        if (names.contains("META-INF/MANIFEST.MF")) ordered.add("META-INF/MANIFEST.MF");

        Collections.sort(names);
        for (String n : names) {
            if (!ordered.contains(n)) ordered.add(n);
        }

        JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tmp));
        try {
            for (String n : ordered) {
                JarEntry out = new JarEntry(n);
                out.setTime(0L); // deterministic timestamp
                jos.putNextEntry(out);
                byte[] data = blobs.get(n);
                if (data != null) jos.write(data);
                jos.closeEntry();
            }
        } finally {
            jos.close();
        }
        Files.move(tmp, jarPath, StandardCopyOption.REPLACE_EXISTING);
    }
    // >>> AUTOGEN: BYTECODEMAPPER IO repackSorted HARDEN END
}
// >>> AUTOGEN: BYTECODEMAPPER IO RemapService END
