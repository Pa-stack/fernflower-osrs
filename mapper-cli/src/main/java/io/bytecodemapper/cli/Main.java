// >>> AUTOGEN: BYTECODEMAPPER CLI Main BEGIN
package io.bytecodemapper.cli;

public final class Main {
    public static void main(String[] args) throws Exception {
    if (args.length == 0 || "--help".equals(args[0]) || "help".equalsIgnoreCase(args[0]) || "-h".equals(args[0])) { usage(); return; }
        if ("printIdf".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            PrintIdf.run(tail);
            return;
        }
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main classMatch DISPATCH BEGIN
        if ("classMatch".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            ClassMatch.run(tail);
            return;
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main classMatch DISPATCH END
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main methodMatch DISPATCH BEGIN
        if ("methodMatch".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            MethodMatch.run(tail);
            return;
        }
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main fieldMatch DISPATCH BEGIN
        if ("fieldMatch".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            FieldMatch.run(tail);
            return;
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main fieldMatch DISPATCH END
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main methodMatch DISPATCH END
        if ("mapOldNew".equals(args[0])) {
            // Delegate to scoped command implementation
            MapOldNew.run(args);
            return;
        }
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main BENCH DISPATCH BEGIN
        if ("bench".equalsIgnoreCase(args[0])) {
            // If manifest provided, use manifest-based bench; otherwise fallback to directory bench
            boolean hasManifest = false;
            for (int i=1;i<args.length;i++) {
                if ("--manifest".equals(args[i])) { hasManifest = true; break; }
            }
            if (hasManifest) {
                String[] tail = new String[Math.max(0, args.length - 1)];
                if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
                int rc = io.bytecodemapper.cli.Bench.run(tail);
                System.exit(rc);
                return;
            } else {
                // Legacy bench path that benchmarks a directory of weekly jars
                java.util.Map<String,String> parsed = new java.util.LinkedHashMap<String,String>();
                for (int i=1;i<args.length;i++) {
                    String a = args[i];
                    int eq = a.indexOf('=');
                    if (a.startsWith("--") && eq>0) parsed.put(a.substring(0,eq), a.substring(eq+1));
                    else if (a.startsWith("--") && i+1<args.length && !args[i+1].startsWith("--")) { parsed.put(a, args[++i]); }
                }
                int rc = io.bytecodemapper.cli.bench.BenchCommand.run(parsed);
                System.exit(rc);
                return;
            }
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main BENCH DISPATCH END
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main tinyStats DISPATCH BEGIN
        if ("tinyStats".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            io.bytecodemapper.cli.TinyStats.run(tail);
            return;
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main tinyStats DISPATCH END
        // >>> AUTOGEN: BYTECODEMAPPER CLI Main applyMappings DISPATCH BEGIN
        if ("applyMappings".equalsIgnoreCase(args[0])) {
            String[] tail = new String[Math.max(0, args.length - 1)];
            if (tail.length > 0) System.arraycopy(args, 1, tail, 0, tail.length);
            io.bytecodemapper.cli.ApplyMappings.run(tail);
            return;
        }
        // <<< AUTOGEN: BYTECODEMAPPER CLI Main applyMappings DISPATCH END
        System.err.println("Unknown command: " + args[0]);
        System.exit(2);
    }

    // >>> AUTOGEN: BYTECODEMAPPER CLI Main USAGE ORCH FLAGS BEGIN
    private static void usage() {
        System.out.println("Commands:");
    System.out.println("  mapOldNew --old <old.jar> --new <new.jar> --out <mappings.tiny> \n" +
        "           [--deterministic] [--cacheDir <dir>] [--idf <path>]\n" +
        "           [--refine] [--refineIters <0|1>] [--lambda <0.0..1.0>]\n" +
        "           [--tauAcceptMethods <0..1>] [--marginMethods <0..1>]\n" +
    "           [--debug-stats] [--debug-normalized [path]] [--debug-sample <N>] [--maxMethods <N>]\n" +
    "           [--wCalls <0..1>] [--wMicro <0..1>] [--wNorm <0..1>] [--wStrings <0..1>] [--wFields <0..1>] [--alphaMicro <0..1>]\n" +
    "           [--dump-normalized-features[=<dir>]] [--nsf-tier-order \"exact,near,wl,wlrelaxed\"]");
    System.out.println("  applyMappings --inJar <in.jar> --mappings <mappings.tiny> --out <out.jar> [--format=tiny2|enigma] [--remapper=tiny|asm] [--verifyRemap] [--deterministic]");
    // >>> AUTOGEN: BYTECODEMAPPER CLI Main BENCH USAGE BEGIN
    // Bench using explicit manifest of pairs
    System.out.println("  bench --manifest <pairs.json> [--outDir <dir>] [--metricsOut <metrics.json>] [--deterministic]");
    // >>> AUTOGEN: BYTECODEMAPPER CLI Main BENCH USAGE END
        System.out.println("  tinyStats --in <mappings.tiny> [--list N]");
        System.out.println("  printIdf --out <path> [--from <existing.properties>] [--lambda 0.9]");
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI Main USAGE ORCH FLAGS END
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Main END
