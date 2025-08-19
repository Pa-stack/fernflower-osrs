// >>> AUTOGEN: BYTECODEMAPPER CLI TinyStats ENHANCED BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.io.tiny.TinyV2Mappings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TinyStats {
    public static void run(String[] args) throws Exception {
        String in = null;
        int list = 5; // how many class pairs to show
        for (int i=0;i<args.length;i++) {
            if ("--in".equals(args[i]) && i+1<args.length) in = args[++i];
            else if ("--list".equals(args[i]) && i+1<args.length) try { list = Integer.parseInt(args[++i]); } catch (NumberFormatException ignore) {}
        }
        if (in == null) {
            System.err.println("Usage: tinyStats --in <mappings.tiny> [--list N]");
            System.exit(2); return;
        }
        Path p = io.bytecodemapper.cli.util.CliPaths.resolveInput(in);
        TinyV2Mappings t = TinyV2Mappings.read(p);
        System.out.println("tinyStats: " + p.toAbsolutePath());

        int c = t.classMap.size();
        int f = t.fieldMap.size();
        int m = t.methodMap.size();

        int cId = 0;
        for (Map.Entry<String,String> e : t.classMap.entrySet()) {
            if (e.getKey().equals(e.getValue())) cId++;
        }
        int cNonId = c - cId;

        System.out.println(" classes: " + c + " (identity=" + cId + ", nonIdentity=" + cNonId + ")");
        System.out.println(" fields : " + f);
        System.out.println(" methods: " + m);

        List<String> keys = new ArrayList<String>(t.classMap.keySet());
        Collections.sort(keys);
        int show = Math.min(list, keys.size());
        if (show > 0) {
            System.out.println(" sample class pairs:");
            for (int i=0; i<show; i++) {
                String obf = keys.get(i);
                String neo = t.classMap.get(obf);
                System.out.println("  c " + obf + " -> " + neo);
            }
        }
    }
    private TinyStats(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI TinyStats ENHANCED END
