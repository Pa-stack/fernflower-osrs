// >>> AUTOGEN: BYTECODEMAPPER core ClasspathScanner BEGIN
package io.bytecodemapper.core.fingerprint;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Minimal classpath scanner for .class entries in jars or directories. */
public final class ClasspathScanner {
    public interface Sink { void accept(ClassNode cn); }

    public void scan(File root, Sink sink) throws IOException {
        if (root.isDirectory()) scanDir(root, sink);
        else if (isJar(root.getName())) scanJar(root, sink);
        else if (root.getName().endsWith(".class")) readClass(root, sink);
    }

    private static boolean isJar(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jar") || n.endsWith(".zip");
    }

    private static void readClass(File file, Sink sink) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ClassReader cr = new ClassReader(in);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_FRAMES);
            sink.accept(cn);
        } finally {
            in.close();
        }
    }

    private static void scanDir(File dir, Sink sink) throws IOException {
        List<File> files = new ArrayList<File>();
        collect(dir, files);
        // Deterministic order: lexicographic by path
        Collections.sort(files, new Comparator<File>() {
            public int compare(File a, File b) { return a.getPath().compareTo(b.getPath()); }
        });
        for (File f : files) if (f.getName().endsWith(".class")) readClass(f, sink);
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, out);
            else out.add(k);
        }
    }

    private static void scanJar(File jar, Sink sink) throws IOException {
        JarFile jf = new JarFile(jar);
        try {
            List<JarEntry> entries = new ArrayList<JarEntry>();
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements();) entries.add(e.nextElement());
            Collections.sort(entries, new Comparator<JarEntry>() {
                public int compare(JarEntry a, JarEntry b) { return a.getName().compareTo(b.getName()); }
            });
            for (JarEntry je : entries) {
                if (je.isDirectory()) continue;
                if (!je.getName().endsWith(".class")) continue;
                InputStream in = jf.getInputStream(je);
                try {
                    ClassReader cr = new ClassReader(in);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, ClassReader.SKIP_FRAMES);
                    sink.accept(cn);
                } finally { in.close(); }
            }
        } finally {
            jf.close();
        }
    }
}
// <<< AUTOGEN: BYTECODEMAPPER core ClasspathScanner END
