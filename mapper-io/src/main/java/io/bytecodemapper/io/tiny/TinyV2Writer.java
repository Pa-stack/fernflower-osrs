// >>> AUTOGEN: BYTECODEMAPPER io TinyV2Writer BEGIN
package io.bytecodemapper.io.tiny;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Minimal/Tiny v2 writer with exactly two namespaces: obf -> deobf.
 * Header:  tiny\t2\t0\tobf\tdeobf
 * Class:   c\t<owner_obf>\t<owner_deobf>
 * Field:   f\t<owner_obf>\t<desc>\t<name_obf>\t<name_deobf>
 * Method:  m\t<owner_obf>\t<desc>\t<name_obf>\t<name_deobf>
 *
 * Deterministic: entries are sorted lexicographically by (owner,name,desc).
 */
public final class TinyV2Writer {

    public static final String NS_FROM = "obf";
    public static final String NS_TO   = "deobf";

    public static final class MethodEntry {
        public final String ownerFrom, nameFrom, desc, nameTo;
        public MethodEntry(String ownerFrom, String nameFrom, String desc, String nameTo) {
            this.ownerFrom = ownerFrom; this.nameFrom = nameFrom; this.desc = desc; this.nameTo = nameTo;
        }
    }

    public static final class FieldEntry {
        public final String ownerFrom, nameFrom, desc, nameTo;
        public FieldEntry(String ownerFrom, String nameFrom, String desc, String nameTo) {
            this.ownerFrom = ownerFrom; this.nameFrom = nameFrom; this.desc = desc; this.nameTo = nameTo;
        }
    }

    public static void writeTiny2(
            Path out,
            Map<String,String> classMap,            // obf owner -> deobf owner
            List<MethodEntry> methods,              // owner_obf, name_obf, desc, name_deobf
            List<FieldEntry> fields                 // owner_obf, name_obf, desc, name_deobf
    ) throws IOException {
        Files.createDirectories(out.getParent());
        BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8);

        // Header
        w.write("tiny\t2\t0\t"); w.write(NS_FROM); w.write('\t'); w.write(NS_TO); w.write('\n');

        // Classes
        List<Map.Entry<String,String>> clEntries = new ArrayList<Map.Entry<String,String>>(classMap.entrySet());
        Collections.sort(clEntries, new Comparator<Map.Entry<String, String>>() {
            @Override public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return a.getKey().compareTo(b.getKey());
            }
        });
        for (Map.Entry<String,String> e : clEntries) {
            w.write("c\t"); w.write(e.getKey()); w.write('\t'); w.write(e.getValue()); w.write('\n');
        }

        // Fields
        List<FieldEntry> fs = new ArrayList<FieldEntry>(fields);
        Collections.sort(fs, new Comparator<FieldEntry>() {
            @Override public int compare(FieldEntry a, FieldEntry b) {
                int c = a.ownerFrom.compareTo(b.ownerFrom); if (c!=0) return c;
                c = a.nameFrom.compareTo(b.nameFrom); if (c!=0) return c;
                return a.desc.compareTo(b.desc);
            }
        });
        for (FieldEntry f : fs) {
            w.write("f\t"); w.write(f.ownerFrom); w.write('\t'); w.write(f.desc);
            w.write('\t'); w.write(f.nameFrom); w.write('\t'); w.write(f.nameTo); w.write('\n');
        }

        // Methods
        List<MethodEntry> ms = new ArrayList<MethodEntry>(methods);
        Collections.sort(ms, new Comparator<MethodEntry>() {
            @Override public int compare(MethodEntry a, MethodEntry b) {
                int c = a.ownerFrom.compareTo(b.ownerFrom); if (c!=0) return c;
                c = a.nameFrom.compareTo(b.nameFrom); if (c!=0) return c;
                return a.desc.compareTo(b.desc);
            }
        });
        for (MethodEntry m : ms) {
            w.write("m\t"); w.write(m.ownerFrom); w.write('\t'); w.write(m.desc);
            w.write('\t'); w.write(m.nameFrom); w.write('\t'); w.write(m.nameTo); w.write('\n');
        }

        w.flush(); w.close();
    }

    private TinyV2Writer(){}
}
// <<< AUTOGEN: BYTECODEMAPPER io TinyV2Writer END
