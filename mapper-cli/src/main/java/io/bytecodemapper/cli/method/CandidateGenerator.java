// >>> AUTOGEN: BYTECODEMAPPER CLI CandidateGenerator BEGIN
package io.bytecodemapper.cli.method;

import java.util.*;

public final class CandidateGenerator {

    public static final int DEFAULT_TOPK = 7;

    public static List<MethodFeatures> topKByWl(MethodFeatures src,
                                                List<MethodFeatures> targets,
                                                int k) {
        ArrayList<Scored> all = new ArrayList<Scored>(targets.size());
        for (MethodFeatures t : targets) {
            int hd = hamming64(src.wlSignature ^ t.wlSignature);
            all.add(new Scored(t, hd));
        }
        Collections.sort(all, new Comparator<Scored>() {
            public int compare(Scored a, Scored b) {
                int c = Integer.compare(a.hamming, b.hamming);
                if (c != 0) return c;
                // stable tie-break: by name+desc asc
                String sa = a.m.ref.name + a.m.ref.desc;
                String sb = b.m.ref.name + b.m.ref.desc;
                return sa.compareTo(sb);
            }
        });
        int n = Math.min(k, all.size());
        ArrayList<MethodFeatures> out = new ArrayList<MethodFeatures>(n);
        for (int i=0;i<n;i++) out.add(all.get(i).m);
        return out;
    }

    private static int hamming64(long x) {
        x = x - ((x >>> 1) & 0x5555555555555555L);
        x = (x & 0x3333333333333333L) + ((x >>> 2) & 0x3333333333333333L);
        x = (x + (x >>> 4)) & 0x0f0f0f0f0f0f0f0fL;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        x = x + (x >>> 32);
        return (int)(x & 0x7f);
    }

    private static final class Scored {
        final MethodFeatures m; final int hamming;
        Scored(MethodFeatures m, int h) { this.m=m; this.hamming=h; }
    }

    private CandidateGenerator(){}
}
// <<< AUTOGEN: BYTECODEMAPPER CLI CandidateGenerator END
