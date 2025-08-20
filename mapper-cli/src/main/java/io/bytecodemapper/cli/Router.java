// >>> AUTOGEN: BYTECODEMAPPER CLI Main RUNNER BEGIN
package io.bytecodemapper.cli;

final class Router {
    static int dispatch(String[] args) throws Exception {
        if (args == null || args.length == 0) { MainUsage.print(); return 0; }
        String cmd = args[0];
        String[] rest = new String[Math.max(0, args.length - 1)];
        if (rest.length > 0) System.arraycopy(args, 1, rest, 0, rest.length);

        if ("help".equalsIgnoreCase(cmd) || "-h".equals(cmd) || "--help".equals(cmd)) { MainUsage.print(); return 0; }

        if ("mapOldNew".equalsIgnoreCase(cmd)) {
            io.bytecodemapper.cli.MapOldNew.run(rest);
            return 0;
        }
        if ("applyMappings".equalsIgnoreCase(cmd)) {
            io.bytecodemapper.cli.ApplyMappings.run(rest);
            return 0; // ApplyMappings may System.exit on usage in Main path; here we assume correct args in tests
        }
        if ("bench".equalsIgnoreCase(cmd)) {
            // Bench returns int in its run method
            try {
                return io.bytecodemapper.cli.Bench.run(rest);
            } catch (Throwable t) {
                t.printStackTrace();
                return 1;
            }
        }
        if ("tinyStats".equalsIgnoreCase(cmd)) {
            io.bytecodemapper.cli.TinyStats.run(rest);
            return 0;
        }
        if ("printIdf".equalsIgnoreCase(cmd)) {
            io.bytecodemapper.cli.PrintIdf.run(rest);
            return 0;
        }

        System.err.println("Unknown command: " + cmd);
        return 2;
    }

    private Router(){}
}

final class MainUsage {
    static void print() {
        // Mirror Main.usage() without exiting
        System.out.println("Commands:");
        System.out.println("  mapOldNew --old <old.jar> --new <new.jar> --out <mappings.tiny> ");
        System.out.println("           [--deterministic] [--cacheDir <dir>] [--idf <path>]");
        System.out.println("           [--refine] [--refineIters <0|1>] [--lambda <0.0..1.0>]");
        System.out.println("           [--tauAcceptMethods <0..1>] [--marginMethods <0..1>]");
        System.out.println("           [--debug-stats] [--debug-normalized [path]] [--debug-sample <N>] [--maxMethods <N>]");
        System.out.println("  applyMappings --inJar <in.jar> --mappings <mappings.tiny> --out <out.jar> [--format=tiny2|enigma] [--remapper=tiny|asm] [--verifyRemap] [--deterministic]");
        System.out.println("  bench --manifest <pairs.json> [--outDir <dir>] [--metricsOut <metrics.json>] [--deterministic]");
        System.out.println("  tinyStats --in <mappings.tiny> [--list N]");
        System.out.println("  printIdf --out <path> [--from <existing.properties>] [--lambda 0.9]");
    }
    private MainUsage(){}
}
// >>> AUTOGEN: BYTECODEMAPPER CLI Main RUNNER END
