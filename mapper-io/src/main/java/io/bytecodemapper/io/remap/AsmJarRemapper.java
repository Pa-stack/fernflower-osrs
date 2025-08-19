// >>> AUTOGEN: BYTECODEMAPPER io AsmJarRemapper ORDER+MANIFEST BEGIN
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

        // Read all entries; keep natural String order. We'll write MANIFEST first if present.
        Map<String, byte[]> entries = new TreeMap<String, byte[]>(); // natural (case-sensitive) ordering
        try (JarInputStream jin = new JarInputStream(Files.newInputStream(inJar))) {
            for (ZipEntry e; (e = jin.getNextJarEntry()) != null; ) {
                if (e.isDirectory()) continue;
                entries.put(e.getName(), readAllBytes(jin));
            }
        }

        // Reverse class map for owner fallback (deobf -> obf)
        final Map<String, String> classMapRev = new TreeMap<String, String>();
        for (Map.Entry<String, String> ce : t.classMap.entrySet()) {
            classMapRev.put(ce.getValue(), ce.getKey());
        }

        final Remapper remapper = new MapBackedRemapper(t, classMapRev);

        try (JarOutputStream jout = new JarOutputStream(Files.newOutputStream(outJar))) {
            // 1) Write MANIFEST first if present
            byte[] manifestBytes = entries.remove("META-INF/MANIFEST.MF");
            if (manifestBytes != null) {
                ZipEntry ze = new ZipEntry("META-INF/MANIFEST.MF");
                jout.putNextEntry(ze);
                jout.write(manifestBytes);
                jout.closeEntry();
            }

            // 2) Then write everything else in deterministic order
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                String name = en.getKey();
                byte[] data = en.getValue();

                if (!name.endsWith(".class")) {
                    ZipEntry ze = new ZipEntry(name);
                    jout.putNextEntry(ze);
                    jout.write(data);
                    jout.closeEntry();
                    continue;
                }

                ClassReader cr = new ClassReader(data);
                ClassWriter cw = new ClassWriter(0);
                ClassRemapper rv = new ClassRemapper(cw, remapper);
                cr.accept(rv, 0);
                byte[] outBytes = cw.toByteArray();

                // Rename entry to remapped internal name
                ClassReader crOut = new ClassReader(outBytes);
                String newInternalName = crOut.getClassName();
                String newEntryName = newInternalName + ".class";

                ZipEntry ze = new ZipEntry(newEntryName);
                jout.putNextEntry(ze);
                jout.write(outBytes);
                jout.closeEntry();
            }
            jout.finish();
        }
    }

    /** Remapper using tiny v2 maps (obf -> deobf), with reverse-owner fallback for fields/methods. */
    private static final class MapBackedRemapper extends Remapper {
        final Map<String, String> classMap;     // obf -> deobf
        final Map<String, String> methodMap;    // owner#name(desc) -> name_deobf
        final Map<String, String> fieldMap;     // owner#name:desc   -> name_deobf
        final Map<String, String> classMapRev;  // deobf -> obf

        MapBackedRemapper(TinyV2Mappings t, Map<String, String> classMapRev) {
            this.classMap = t.classMap;
            this.methodMap = t.methodMap;
            this.fieldMap = t.fieldMap;
            this.classMapRev = classMapRev;
        }

        @Override public String map(String internalName) {
            String n = classMap.get(internalName);
            return n != null ? n : internalName;
        }

        @Override public String mapFieldName(String owner, String name, String descriptor) {
            String key1 = owner + "#" + name + ":" + descriptor;
            String mapped = fieldMap.get(key1);
            if (mapped != null) return mapped;

            String obfOwner = classMapRev.get(owner);
            if (obfOwner != null) {
                String key2 = obfOwner + "#" + name + ":" + descriptor;
                String m2 = fieldMap.get(key2);
                if (m2 != null) return m2;
            }
            return name;
        }

        @Override public String mapMethodName(String owner, String name, String descriptor) {
            String key1 = owner + "#" + name + descriptor;
            String mapped = methodMap.get(key1);
            if (mapped != null) return mapped;

            String obfOwner = classMapRev.get(owner);
            if (obfOwner != null) {
                String key2 = obfOwner + "#" + name + descriptor;
                String m2 = methodMap.get(key2);
                if (m2 != null) return m2;
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
// <<< AUTOGEN: BYTECODEMAPPER io AsmJarRemapper ORDER+MANIFEST END
