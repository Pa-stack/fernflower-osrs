// >>> AUTOGEN: BYTECODEMAPPER CLI ApplyMappings BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.io.remap.RemapService;
import java.io.File;

public final class ApplyMappings {

    public static void run(String[] args) throws Exception {
        File inJar = null, mappings = null, outJar = null;
        RemapService.MappingFormat fmt = RemapService.MappingFormat.TINY2;
        RemapService.RemapperKind kind = RemapService.RemapperKind.TINY; // default: TinyRemapper
        boolean verify = false;
        boolean deterministic = true; // enforce deterministic jar order

        for (int i=0; i<args.length; i++) {
            String a = args[i];
            if ("--inJar".equals(a) && i+1<args.length) inJar = new File(args[++i]);
            else if ("--mappings".equals(a) && i+1<args.length) mappings = new File(args[++i]);
            else if ("--out".equals(a) && i+1<args.length) outJar = new File(args[++i]);
            else if ("--format".equals(a) && i+1<args.length) {
                String v = args[++i];
                fmt = "enigma".equalsIgnoreCase(v) ? RemapService.MappingFormat.ENIGMA : RemapService.MappingFormat.TINY2;
            } else if ("--remapper".equals(a) && i+1<args.length) {
                String v = args[++i];
                kind = "asm".equalsIgnoreCase(v) ? RemapService.RemapperKind.ASM : RemapService.RemapperKind.TINY;
            } else if ("--verifyRemap".equals(a)) verify = true;
            else if ("--deterministic".equals(a)) deterministic = true;
        }

        if (inJar == null || mappings == null || outJar == null) {
            System.err.println("Usage: applyMappings --inJar <in.jar> --mappings <map.tiny> --out <out.jar> [--format=tiny2|enigma] [--remapper=tiny|asm] [--verifyRemap] [--deterministic]");
            System.exit(2);
            return;
        }

        RemapService.VerifyStats vs = RemapService.applyMappings(inJar, mappings, outJar, fmt, kind, verify, deterministic);
        if (verify) System.out.println("[applyMappings] " + vs);
    }

    private ApplyMappings(){}
}
// >>> AUTOGEN: BYTECODEMAPPER CLI ApplyMappings END
