// >>> AUTOGEN: BYTECODEMAPPER CLI ApplyMappings BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.io.remap.AsmJarRemapper;
import io.bytecodemapper.io.tiny.TinyV2Mappings;

import java.util.Locale;

public final class ApplyMappings {

    public static void run(String[] args) throws Exception {
        String inJar = null, mappings = null, outJar = null;
        String format = "tiny2"; // tiny2 default
        String ns = "obf,deobf"; // reserved for future use (two namespaces only)
        String remapper = "asm";  // asm (default), other options in future (tinyremapper,specialsource)
        // >>> AUTOGEN: BYTECODEMAPPER CLI ApplyMappings VERIFY BEGIN
        boolean verify = false;

        for (int i=0;i<args.length;i++) {
            String a = args[i];
            if ("--inJar".equals(a) && i+1<args.length) inJar = args[++i];
            else if ("--mappings".equals(a) && i+1<args.length) mappings = args[++i];
            else if ("--out".equals(a) && i+1<args.length) outJar = args[++i];
            else if ("--format".equals(a) && i+1<args.length) format = args[++i];
            else if ("--ns".equals(a) && i+1<args.length) ns = args[++i];
            else if ("--remapper".equals(a) && i+1<args.length) remapper = args[++i];
            else if ("--verifyRemap".equals(a)) verify = true;
        }

        if (inJar == null || mappings == null || outJar == null) {
            System.err.println("Usage: applyMappings --inJar <in.jar> --mappings <mappings.tiny> --out <out.jar> [--format tiny2] [--ns obf,deobf] [--remapper asm]");
            System.exit(2);
            return;
        }

    // >>> AUTOGEN: BYTECODEMAPPER CLI ApplyMappings INPUT RESOLUTION BEGIN
    // Use CliPaths.resolveInput for inputs, resolveOutput for outputs
    java.nio.file.Path inPath  = io.bytecodemapper.cli.util.CliPaths.resolveInput(inJar);
    java.nio.file.Path mapPath = io.bytecodemapper.cli.util.CliPaths.resolveInput(mappings);
    java.nio.file.Path outPath = io.bytecodemapper.cli.util.CliPaths.resolveOutput(outJar);
    // <<< AUTOGEN: BYTECODEMAPPER CLI ApplyMappings INPUT RESOLUTION END

        if (!"tiny2".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("Only tiny2 is supported in this build.");
        }
        if (!"asm".equalsIgnoreCase(remapper)) {
            System.out.println("Warning: remapper=" + remapper + " requested but not available; falling back to asm.");
        }
        // Consume --ns to avoid unused variable and provide early validation for future.
        String nsLower = ns == null ? "" : ns.toLowerCase(Locale.ROOT).trim();
        if (!nsLower.isEmpty() && !"obf,deobf".equals(nsLower)) {
            System.out.println("Warning: --ns=" + ns + " is not supported in this build; assuming obf,deobf");
        }

    TinyV2Mappings t = TinyV2Mappings.read(mapPath);
    AsmJarRemapper.remapJar(inPath, outPath, t);
    System.out.println("Remapped jar written to: " + outPath);
    // >>> AUTOGEN: BYTECODEMAPPER CLI ApplyMappings LOG NOTE BEGIN
    System.out.println("(Note) Class entry names are renamed to match remapped internal names.");
    // <<< AUTOGEN: BYTECODEMAPPER CLI ApplyMappings LOG NOTE END
    if (verify) {
        int inClasses  = countClasses(inPath);
        int outClasses = countClasses(outPath);
        String sample  = firstClass(outPath);
        System.out.println("[verify] classes in=" + inClasses + " out=" + outClasses + " sample=" + sample);
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI ApplyMappings VERIFY END
    }

    private ApplyMappings(){}

    // >>> AUTOGEN: BYTECODEMAPPER CLI ApplyMappings VERIFY BEGIN
    private static int countClasses(java.nio.file.Path jar) throws Exception {
        int n=0;
        java.util.jar.JarInputStream jin = new java.util.jar.JarInputStream(java.nio.file.Files.newInputStream(jar));
        for (java.util.zip.ZipEntry e; (e=jin.getNextJarEntry())!=null; ) if (!e.isDirectory() && e.getName().endsWith(".class")) n++;
        jin.close(); return n;
    }
    private static String firstClass(java.nio.file.Path jar) throws Exception {
        java.util.jar.JarInputStream jin = new java.util.jar.JarInputStream(java.nio.file.Files.newInputStream(jar));
        for (java.util.zip.ZipEntry e; (e=jin.getNextJarEntry())!=null; ) if (!e.isDirectory() && e.getName().endsWith(".class")) { jin.close(); return e.getName(); }
        jin.close(); return "<none>";
    }
    // <<< AUTOGEN: BYTECODEMAPPER CLI ApplyMappings VERIFY END
}
// <<< AUTOGEN: BYTECODEMAPPER CLI ApplyMappings END
