// >>> AUTOGEN: BYTECODEMAPPER io AsmJarRemapper BEGIN
package io.bytecodemapper.io.remap;

import io.bytecodemapper.io.tiny.TinyV2Mappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;

public final class AsmJarRemapper {

    public static void remapJar(Path inJar, Path outJar, TinyV2Mappings t) throws IOException {
        Files.createDirectories(outJar.getParent());

        Map<String,byte[]> entries = new TreeMap<String,byte[]>(String.CASE_INSENSITIVE_ORDER);
        // read
        JarInputStream jin = new JarInputStream(Files.newInputStream(inJar));
        JarEntry e;
        while ((e = jin.getNextJarEntry()) != null) {
            if (e.isDirectory()) continue;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(4096, (int)e.getSize()));
            byte[] buf = new byte[8192];
            int r;
            while ((r = jin.read(buf)) != -1) bos.write(buf, 0, r);
            entries.put(e.getName(), bos.toByteArray());
        }
        jin.close();

        // remap
        Remapper remapper = new MapBackedRemapper(t);
        Map<String,byte[]> out = new TreeMap<String,byte[]>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String,byte[]> en : entries.entrySet()) {
            String name = en.getKey();
            byte[] data = en.getValue();
            if (!name.endsWith(".class")) {
                // non-class: copy unchanged
                out.put(name, data);
                continue;
            }
            ClassReader cr = new ClassReader(data);
            ClassWriter cw = new ClassWriter(0);
            ClassRemapper rv = new ClassRemapper(cw, remapper);
            cr.accept(rv, 0);
            out.put(name, cw.toByteArray());
        }

        // write jar (deterministic order)
        JarOutputStream jout = new JarOutputStream(Files.newOutputStream(outJar));
        for (Map.Entry<String,byte[]> en : out.entrySet()) {
            String name = en.getKey();
            byte[] data = en.getValue();
            ZipEntry ze = new ZipEntry(name);
            jout.putNextEntry(ze);
            jout.write(data);
            jout.closeEntry();
        }
        jout.finish();
        jout.close();
    }

    /** Custom Remapper that uses tiny v2 maps (obf -> deobf). */
    private static final class MapBackedRemapper extends Remapper {
        final Map<String,String> classMap;
        final Map<String,String> methodMap; // key: owner#name(desc) obf -> name_deobf
        final Map<String,String> fieldMap;  // key: owner#name:desc   obf -> name_deobf

        MapBackedRemapper(TinyV2Mappings t) {
            this.classMap = t.classMap;
            this.methodMap = t.methodMap;
            this.fieldMap  = t.fieldMap;
        }

        @Override
        public String map(String internalName) {
            String n = classMap.get(internalName);
            return n != null ? n : internalName;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String key = owner + "#" + name + ":" + descriptor;
            String mapped = fieldMap.get(key);
            return mapped != null ? mapped : name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            String key = owner + "#" + name + descriptor;
            String mapped = methodMap.get(key);
            return mapped != null ? mapped : name;
        }
    }

    private AsmJarRemapper(){}
}
// <<< AUTOGEN: BYTECODEMAPPER io AsmJarRemapper END
