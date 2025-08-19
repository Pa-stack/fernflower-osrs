// >>> AUTOGEN: BYTECODEMAPPER CLI FieldMatch BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.field.FieldMatcher;
import io.bytecodemapper.cli.field.FieldRef;
import io.bytecodemapper.cli.method.MethodRef;
import io.bytecodemapper.cli.util.CliPaths;
// >>> AUTOGEN: BYTECODEMAPPER CLI FieldMatch use MethodMapParser BEGIN
import io.bytecodemapper.cli.util.MethodMapParser;
// <<< AUTOGEN: BYTECODEMAPPER CLI FieldMatch use MethodMapParser END
import io.bytecodemapper.core.fingerprint.ClasspathScanner;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class FieldMatch {

    public static void run(String[] args) throws Exception {
        Path oldJar=null, newJar=null, methodMapPath=null, outPath=null;

        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if ("--old".equals(a) && i+1<args.length) oldJar = CliPaths.resolveInput(args[++i]);
            else if ("--new".equals(a) && i+1<args.length) newJar = CliPaths.resolveInput(args[++i]);
            else if ("--methodMap".equals(a) && i+1<args.length) methodMapPath = CliPaths.resolveInput(args[++i]);
            else if ("--out".equals(a) && i+1<args.length) outPath = CliPaths.resolveOutput(args[++i]);
        }
        if (oldJar==null || newJar==null || methodMapPath==null || outPath==null) {
            System.err.println("Usage: fieldMatch --old <old.jar> --new <new.jar> --methodMap <path> --out <path>");
            return;
        }

    if (outPath.getParent()!=null) Files.createDirectories(outPath.getParent());

    // >>> AUTOGEN: BYTECODEMAPPER CLI FieldMatch use MethodMapParser BEGIN
    // Parse method map (tolerant to multiple formats)
    MethodMapParser.Parsed parsed = MethodMapParser.parse(methodMapPath);
    Map<MethodRef, MethodRef> methodMap = parsed.methodMap;
    Map<String,String> classMap = parsed.classMap;
    System.out.println("Loaded method pairs: " + methodMap.size() + " (owners imply " + classMap.size() + " class mappings)");
    // <<< AUTOGEN: BYTECODEMAPPER CLI FieldMatch use MethodMapParser END

        // Load classes deterministically
        final List<ClassNode> oldList = new ArrayList<ClassNode>();
        final List<ClassNode> newList = new ArrayList<ClassNode>();
        ClasspathScanner scanner = new ClasspathScanner();
        scanner.scan(oldJar.toFile(), new ClasspathScanner.Sink() { public void accept(ClassNode cn) { oldList.add(cn); } });
        scanner.scan(newJar.toFile(), new ClasspathScanner.Sink() { public void accept(ClassNode cn) { newList.add(cn); } });

        Map<String, ClassNode> oldByName = indexByName(oldList);
        Map<String, ClassNode> newByName = indexByName(newList);

        Map<FieldRef, FieldRef> fields = FieldMatcher.matchFields(classMap, methodMap, oldByName, newByName);

        int matched = fields.size();
        int abstained = 0;

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath))) {
            pw.println("# BytecodeMapper Field Map (conservative)");
            for (Map.Entry<FieldRef, FieldRef> e : fields.entrySet()) {
                pw.println(e.getKey().toString() + " -> " + e.getValue().toString());
            }
            // Enumerate abstentions: old fields of mapped owners not emitted
            Set<FieldRef> emitted = fields.keySet();
            for (String o : classMap.keySet()) {
                ClassNode cn = oldByName.get(o);
                if (cn == null || cn.fields == null) continue;
                @SuppressWarnings("unchecked")
                java.util.List<org.objectweb.asm.tree.FieldNode> fl = (java.util.List<org.objectweb.asm.tree.FieldNode>) (java.util.List<?>) cn.fields;
                for (org.objectweb.asm.tree.FieldNode fn : fl) {
                    FieldRef fr = new FieldRef(o, fn.name, fn.desc);
                    if (!emitted.contains(fr)) { pw.println("# abstain " + fr.toString()); abstained++; }
                }
            }
            pw.flush();
        }

        System.out.println("Field matching complete. matched=" + matched + " abstained=" + abstained);
    }

    private static Map<String,ClassNode> indexByName(List<ClassNode> list) {
        Map<String,ClassNode> m = new LinkedHashMap<String,ClassNode>(list.size()*2);
        for (ClassNode cn : list) m.put(cn.name, cn);
        return m;
    }

    private FieldMatch(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI FieldMatch END
