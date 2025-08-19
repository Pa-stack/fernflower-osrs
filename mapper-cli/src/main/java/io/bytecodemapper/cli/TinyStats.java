// >>> AUTOGEN: BYTECODEMAPPER CLI TinyStats BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.io.tiny.TinyV2Mappings;

import java.nio.file.Path;

public final class TinyStats {
    public static void run(String[] args) throws Exception {
        String in = null;
        for (int i=0;i<args.length;i++) if ("--in".equals(args[i]) && i+1<args.length) in = args[++i];
        if (in == null) {
            System.err.println("Usage: tinyStats --in <mappings.tiny>");
            System.exit(2); return;
        }
        Path p = io.bytecodemapper.cli.util.CliPaths.resolveInput(in);
        TinyV2Mappings t = TinyV2Mappings.read(p);
        System.out.println("tinyStats: " + p.toAbsolutePath());
        System.out.println(" classes: " + t.classMap.size());
        System.out.println(" fields : " + t.fieldMap.size());
        System.out.println(" methods: " + t.methodMap.size());
    }
    private TinyStats(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI TinyStats END
