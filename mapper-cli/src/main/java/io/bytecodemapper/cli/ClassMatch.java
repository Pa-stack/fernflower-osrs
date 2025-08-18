// >>> AUTOGEN: BYTECODEMAPPER CLI ClassMatch BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.core.fingerprint.*;
import io.bytecodemapper.signals.micro.MicroPatternProviderImpl;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class ClassMatch {

    static void run(String[] args) throws Exception {
        File oldJar = null, newJar = null, out = null;
        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if ("--old".equals(a) && i+1<args.length) oldJar = new File(args[++i]);
            else if ("--new".equals(a) && i+1<args.length) newJar = new File(args[++i]);
            else if ("--out".equals(a) && i+1<args.length) out = new File(args[++i]);
            else System.err.println("Unknown or incomplete arg: " + a);
        }
        if (oldJar == null || newJar == null || out == null) {
            throw new IllegalArgumentException("Usage: classMatch --old <old.jar> --new <new.jar> --out <path>");
        }
    oldJar = resolveMaybeModuleRelative(oldJar);
    newJar = resolveMaybeModuleRelative(newJar);
    if (!oldJar.isFile()) throw new FileNotFoundException("old jar not found: " + oldJar);
    if (!newJar.isFile()) throw new FileNotFoundException("new jar not found: " + newJar);
        if (out.getParentFile()!=null) out.getParentFile().mkdirs();

    // 1) Read classes deterministically
    final List<ClassNode> oldClasses = new ArrayList<ClassNode>();
    final List<ClassNode> newClasses = new ArrayList<ClassNode>();
    ClasspathScanner scanner = new ClasspathScanner();
    scanner.scan(oldJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { oldClasses.add(cn); }});
    scanner.scan(newJar, new ClasspathScanner.Sink() { public void accept(ClassNode cn) { newClasses.add(cn); }});

        // 2) Build fingerprints
        MicroPatternProvider micro = new MicroPatternProviderImpl();
        FingerprintBuilder fb = new FingerprintBuilder(micro);
        List<ClassFingerprint> fpOld = buildFingerprints(fb, oldClasses);
        List<ClassFingerprint> fpNew = buildFingerprints(fb, newClasses);

        // 3) Match
        List<GreedyClassMatcher.Pair> pairs = GreedyClassMatcher.match(fpOld, fpNew);

        // 4) Write
        PrintWriter pw = new PrintWriter(out, "UTF-8");
        try {
            for (GreedyClassMatcher.Pair p : pairs) pw.println(p.toString());
        } finally {
            pw.close();
        }
        System.out.println("Wrote class map: " + out.getAbsolutePath() + " (" + pairs.size() + " pairs)");
    }

    private static List<ClassFingerprint> buildFingerprints(FingerprintBuilder fb, List<ClassNode> classes) {
        ArrayList<ClassFingerprint> out = new ArrayList<ClassFingerprint>(classes.size());
        for (ClassNode cn : classes) out.add(fb.build(cn));
        // Deterministic order: by internal name
        Collections.sort(out, new Comparator<ClassFingerprint>() {
            public int compare(ClassFingerprint a, ClassFingerprint b) { return a.internalName().compareTo(b.internalName()); }
        });
        return out;
    }

    private static File resolveMaybeModuleRelative(File f) throws java.io.IOException {
        if (f.isAbsolute() || f.isFile()) return f;
        // When running :mapper-cli:run, CWD is mapper-cli/. Try parent (repo root) as fallback.
        File cwd = new File(".").getCanonicalFile();
        File parent = cwd.getParentFile();
        if (parent != null) {
            File alt = new File(parent, f.getPath());
            if (alt.isFile()) return alt;
        }
        return f; // return original; caller will error if still missing
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI ClassMatch END
