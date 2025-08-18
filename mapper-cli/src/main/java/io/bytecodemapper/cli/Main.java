// >>> AUTOGEN: BYTECODEMAPPER CLI Main BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.io.TinyLikeWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0])) {
            System.out.println("Commands:");
            System.out.println("  mapOldNew --old <old.jar> --new <new.jar> --out <out.tiny>");
            System.out.println("  applyMappings --inJar <in.jar> --mappings <mappings.tiny> --out <out.jar>");
            System.out.println("  printIdf --out <path> [--from <existing.properties>] [--lambda 0.9]");
            return;
        }
        if ("printIdf".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            PrintIdf.run(tail);
            return;
        }
        if ("mapOldNew".equals(args[0])) {
            File out = new File(findArg(args, "--out", "build/mappings.tiny"));
            // Placeholder: write a dummy class mapping to prove pipeline works.
            List<String[]> pairs = new ArrayList<String[]>();
            pairs.add(new String[]{"a/A", "deob/A"});
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            TinyLikeWriter.writeClasses(out, pairs);
            System.out.println("Wrote mappings: " + out.getAbsolutePath());
            return;
        }
        if ("applyMappings".equals(args[0])) {
            File in = new File(findArg(args, "--inJar", null));
            File map = new File(findArg(args, "--mappings", null));
            File out = new File(findArg(args, "--out", "build/new-mapped.jar"));
            if (in == null || map == null) {
                System.err.println("Missing --inJar or --mappings");
                System.exit(2);
            }
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            // Stub: copy input to output unchanged.
            java.nio.file.Files.copy(in.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Stub-applied mappings (no-op): " + out.getAbsolutePath());
            return;
        }
        System.err.println("Unknown command: " + args[0]);
        System.exit(2);
    }

    private static String findArg(String[] a, String key, String def) {
        for (int i=0;i<a.length-1;i++) if (key.equals(a[i])) return a[i+1];
        return def;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Main END
