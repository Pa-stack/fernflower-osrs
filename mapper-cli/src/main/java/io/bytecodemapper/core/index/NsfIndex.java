// >>> AUTOGEN: BYTECODEMAPPER NSF INDEX BEGIN
package io.bytecodemapper.core.index;

import java.util.*;
import io.bytecodemapper.cli.cache.MethodFeatureCacheEntry;

public final class NsfIndex {
    public static final class NewRef {
        public final String owner, name, desc; public final long nsf64;
        public NewRef(String owner, String name, String desc, long nsf64){ this.owner=owner; this.name=name; this.desc=desc; this.nsf64=nsf64; }
    }
    private final Map<String,ArrayList<NewRef>> byKey = new LinkedHashMap<String,ArrayList<NewRef>>();
    private static String key(String owner, String desc){ return owner + "\u0000" + desc; }

    public void add(String owner, String desc, String name, long nsf64) {
        String k = key(owner, desc);
        ArrayList<NewRef> bucket = byKey.get(k);
        if (bucket == null) { bucket = new ArrayList<NewRef>(); byKey.put(k, bucket); }
        bucket.add(new NewRef(owner,name,desc,nsf64));
    }
    public java.util.List<NewRef> exact(String owner, String desc, long nsf64) {
        ArrayList<NewRef> out = new ArrayList<NewRef>();
        ArrayList<NewRef> b = byKey.get(key(owner, desc)); if (b==null) return out;
        for (NewRef r : b) if (r.nsf64 == nsf64) out.add(r);
        sort(out); return out;
    }
    public java.util.List<NewRef> near(String owner, String desc, long nsf64, int hammingBudget) {
        ArrayList<NewRef> out = new ArrayList<NewRef>();
        ArrayList<NewRef> b = byKey.get(key(owner, desc)); if (b==null) return out;
        // Deterministic scan: filter by popcount XOR e budget
        for (NewRef r : b) {
            int pop = Long.bitCount(r.nsf64 ^ nsf64);
            if (pop <= hammingBudget) out.add(r);
        }
        sort(out); // stable: by name asc
        // Cap to avoid explosion
        int MAX = Math.min(512, out.size());
        return new ArrayList<NewRef>(out.subList(0, MAX));
    }
    private static void sort(ArrayList<NewRef> xs){
        Collections.sort(xs, new Comparator<NewRef>() {
            public int compare(NewRef a, NewRef b){
                int c = a.owner.compareTo(b.owner); if (c!=0) return c;
                c = a.desc.compareTo(b.desc); if (c!=0) return c;
                return a.name.compareTo(b.name);
            }});
    }
}
// >>> AUTOGEN: BYTECODEMAPPER NSF INDEX END
