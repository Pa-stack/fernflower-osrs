// >>> AUTOGEN: BYTECODEMAPPER CLI Main BEGIN
package io.bytecodemapper.cli;

public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0])) {
            System.out.println("Commands:");
            System.out.println("  mapOldNew --old <old.jar> --new <new.jar> --out <out.tiny>");
            System.out.println("             [--debug-normalized [path]] [--debug-sample N]");
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

}
// <<< AUTOGEN: BYTECODEMAPPER CLI Main END
