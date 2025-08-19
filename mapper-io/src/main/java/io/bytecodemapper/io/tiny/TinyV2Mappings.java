// >>> AUTOGEN: BYTECODEMAPPER io TinyV2Mappings BEGIN
package io.bytecodemapper.io.tiny;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Minimal tiny v2 reader for two namespaces: obf -> deobf. */
public final class TinyV2Mappings {

    public final Map<String,String> classMap = new LinkedHashMap<String,String>(); // obf -> deobf
    public final Map<String,String> methodMap = new LinkedHashMap<String,String>(); // key: owner#name(desc) obf -> name_deobf
    public final Map<String,String> fieldMap = new LinkedHashMap<String,String>();  // key: owner#name:desc   obf -> name_deobf

    public static TinyV2Mappings read(Path p) throws IOException {
        TinyV2Mappings t = new TinyV2Mappings();
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            if (header == null || !header.startsWith("tiny\t2\t")) {
                throw new IOException("Not a tiny v2 file: " + p);
            }
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split("\t");
                if (parts.length < 3) continue;
                switch (parts[0]) {
                    case "c":
                        // c  owner_obf  owner_deobf
                        if (parts.length >= 3) {
                            t.classMap.put(parts[1], parts[2]);
                        }
                        break;
                    case "f":
                        // f owner_obf  desc  name_obf  name_deobf
                        if (parts.length >= 5) {
                            String key = parts[1] + "#" + parts[3] + ":" + parts[2];
                            t.fieldMap.put(key, parts[4]);
                        }
                        break;
                    case "m":
                        // m owner_obf  desc  name_obf  name_deobf
                        if (parts.length >= 5) {
                            String key = parts[1] + "#" + parts[3] + parts[2];
                            t.methodMap.put(key, parts[4]);
                        }
                        break;
                    default:
                        // ignore
                }
            }
        }
        return t;
    }

    private TinyV2Mappings(){}
}
// <<< AUTOGEN: BYTECODEMAPPER io TinyV2Mappings END
