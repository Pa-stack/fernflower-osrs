// >>> AUTOGEN: BYTECODEMAPPER CLI MethodMapParser BEGIN
package io.bytecodemapper.cli.util;

import io.bytecodemapper.cli.method.MethodRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Tolerant parser for method map lines:
 *   1) "owner#name(desc) -> owner#name(desc) [score=...]"
 *   2) "owner/name desc -> owner/name desc [score=...]"
 * Ignores blank lines and lines starting with '#'.
 */
public final class MethodMapParser {

    public static final class Parsed {
        public final Map<MethodRef, MethodRef> methodMap;
        public final Map<String,String> classMap;
        Parsed(Map<MethodRef, MethodRef> mm, Map<String,String> cm) { this.methodMap = mm; this.classMap = cm; }
    }

    public static Parsed parse(Path path) throws IOException {
        Map<MethodRef, MethodRef> mm = new LinkedHashMap<MethodRef, MethodRef>();
        Map<String,String> cm = new LinkedHashMap<String,String>();
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int arrow = line.indexOf("->");
            if (arrow < 0) continue;
            String left = line.substring(0, arrow).trim();
            String right = line.substring(arrow + 2).trim();

            // strip trailing tokens like "score=0.6123"
            int sIdx = right.indexOf("score=");
            if (sIdx >= 0) right = right.substring(0, sIdx).trim();

            MethodRef L = parseOne(left);
            MethodRef R = parseOne(right);
            if (L == null || R == null) continue;

            mm.put(L, R);
            cm.put(L.owner, R.owner);
        }
        return new Parsed(mm, cm);
    }

    private static MethodRef parseOne(String s) {
        s = s.trim();

        // Format #1: owner#name(desc)
        int h = s.indexOf('#');
        int l = s.indexOf('(');
        int r = s.lastIndexOf(')');
        if (h > 0 && l > h && r > l) {
            String owner = s.substring(0, h).trim();
            String name  = s.substring(h + 1, l).trim();
            String desc  = s.substring(l, r + 1).trim();
            return new MethodRef(owner, name, desc);
        }

        // Format #2: owner/name desc
        int slash = s.indexOf('/');
        int space = s.indexOf(' ');
        if (slash > 0 && space > slash) {
            String owner = s.substring(0, slash).trim();
            String name  = s.substring(slash + 1, space).trim();
            String desc  = s.substring(space + 1).trim();
            // if desc has extra tokens, keep first token that looks like a descriptor
            int nextSpace = desc.indexOf(' ');
            if (nextSpace > 0) desc = desc.substring(0, nextSpace).trim();
            return new MethodRef(owner, name, desc);
        }

        // Not recognized; ignore the line
        return null;
    }

    private MethodMapParser(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodMapParser END
