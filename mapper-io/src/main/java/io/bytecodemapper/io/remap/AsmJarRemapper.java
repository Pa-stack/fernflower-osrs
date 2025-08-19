// >>> AUTOGEN: BYTECODEMAPPER io AsmJarRemapper RENAMING+FALLBACK BEGIN
package io.bytecodemapper.io.remap;

import io.bytecodemapper.io.tiny.TinyV2Mappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public final class AsmJarRemapper {

    public static void remapJar(Path inJar, Path outJar, TinyV2Mappings t) throws IOException {
        Files.createDirectories(outJar.getParent());

        // Read all entries into memory to allow deterministic write order
        Map<String, byte[]> entries = new TreeMap<String, byte[]>(String.CASE_INSENSITIVE_ORDER);
        try (JarInputStream jin = new JarInputStream(Files.newInputStream(inJar))) {
            for (ZipEntry e; (e = jin.getNextJarEntry()) != null; ) {
                if (e.isDirectory()) continue;
                entries.put(e.getName(), readAllBytes(jin));
            }
        }

        // Build a reverse class map for fallback owner lookups
        final Map<String, String> classMap = t.classMap;           // obf -> deobf
        final Map<String, String> classMapRev = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> ce : classMap.entrySet()) {
            classMapRev.put(ce.getValue(), ce.getKey());
        }

        final Remapper remapper = new MapBackedRemapper(t, classMapRev);

        // Remap and write in deterministic order; rename .class entries to NEW internal name
        try (JarOutputStream jout = new JarOutputStream(Files.newOutputStream(outJar))) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                String name = en.getKey();
                byte[] data = en.getValue();

                if (!name.endsWith(".class")) {
                    // Non-class entries copied verbatim
                    ZipEntry ze = new ZipEntry(name);
                    jout.putNextEntry(ze);
                    jout.write(data);
                    jout.closeEntry();
                    continue;
                }

                // Remap bytecode
                ClassReader cr = new ClassReader(data);
                ClassWriter cw = new ClassWriter(0);
                ClassRemapper rv = new ClassRemapper(cw, remapper);
                cr.accept(rv, 0);
                byte[] outBytes = cw.toByteArray();

                // Determine NEW internal name post-remap for entry rename
                ClassReader crOut = new ClassReader(outBytes);
                String newInternalName = crOut.getClassName(); // e.g., a/b/C

                String newEntryName = newInternalName + ".class";
                ZipEntry ze = new ZipEntry(newEntryName);
                jout.putNextEntry(ze);
                jout.write(outBytes);
                jout.closeEntry();
            }
            jout.finish();
        }
    }

    /** Custom Remapper that uses tiny v2 maps (obf -> deobf) with owner fallback via reverse class map. */
    private static final class MapBackedRemapper extends Remapper {
        final Map<String, String> classMap;     // obf -> deobf
        final Map<String, String> methodMap;    // key: owner#name(desc) obf -> name_deobf
        final Map<String, String> fieldMap;     // key: owner#name:desc   obf -> name_deobf
        final Map<String, String> classMapRev;  // deobf -> obf (fallback)

        MapBackedRemapper(TinyV2Mappings t, Map<String, String> classMapRev) {
            this.classMap = t.classMap;
            this.methodMap = t.methodMap;
            this.fieldMap = t.fieldMap;
            this.classMapRev = classMapRev;
        }

        @Override
        public String map(String internalName) {
            String n = classMap.get(internalName);
            return n != null ? n : internalName;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            // primary: assume owner is obf
            String key = owner + "#" + name + ":" + descriptor;
            String mapped = fieldMap.get(key);
            if (mapped != null) return mapped;

            // fallback: if owner looks deobf, map back to obf and retry
            String obfOwner = classMapRev.get(owner);
            if (obfOwner != null) {
                String key2 = obfOwner + "#" + name + ":" + descriptor;
                String mapped2 = fieldMap.get(key2);
                if (mapped2 != null) return mapped2;
            }
            return name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            // primary: assume owner is obf
            String key = owner + "#" + name + descriptor;
            String mapped = methodMap.get(key);
            if (mapped != null) return mapped;

            // fallback: if owner looks deobf, map back to obf and retry
            String obfOwner = classMapRev.get(owner);
            if (obfOwner != null) {
                String key2 = obfOwner + "#" + name + descriptor;
                String mapped2 = methodMap.get(key2);
                if (mapped2 != null) return mapped2;
            }
            return name;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    private AsmJarRemapper() {}
}
// <<< AUTOGEN: BYTECODEMAPPER io AsmJarRemapper RENAMING+FALLBACK END
