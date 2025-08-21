// >>> AUTOGEN: BYTECODEMAPPER NSF INDEX BEGIN
package io.bytecodemapper.core.index;

import java.util.*;

public final class NsfIndex {
    public enum Mode { CANONICAL, SURROGATE, BOTH }
    public static final class NewRef {
        public final String owner, name, desc; public final long nsf64; public final String bucket;
        public NewRef(String owner, String name, String desc, long nsf64, String bucket){ this.owner=owner; this.name=name; this.desc=desc; this.nsf64=nsf64; this.bucket=bucket; }
    }
    private final Map<String,ArrayList<NewRef>> byKey = new LinkedHashMap<String,ArrayList<NewRef>>();
    private static String key(String owner, String desc){ return owner + "\u0000" + desc; }

    // Add with mode: CANONICAL, SURROGATE, BOTH
    public void add(String owner, String desc, String name, long nsf64, Mode mode) {
        String k = key(owner, desc);
        ArrayList<NewRef> bucket = byKey.get(k);
        if (bucket == null) { bucket = new ArrayList<NewRef>(); byKey.put(k, bucket); }
        if (mode == Mode.BOTH) {
            if (nsf64 != 0L) {
                bucket.add(new NewRef(owner, name, desc, nsf64, "nsf64"));
                bucket.add(new NewRef(owner, name, desc, nsf64, "nsf_surrogate"));
            } else {
                bucket.add(new NewRef(owner, name, desc, nsf64, "nsf_surrogate"));
            }
        } else if (mode == Mode.CANONICAL) {
            bucket.add(new NewRef(owner, name, desc, nsf64, "nsf64"));
        } else {
            bucket.add(new NewRef(owner, name, desc, nsf64, "nsf_surrogate"));
        }
    }

    // Deterministic dedup: favor canonical bucket if ref appears in both
    public java.util.List<NewRef> exact(String owner, String desc, long nsf64) {
        ArrayList<NewRef> b = byKey.get(key(owner, desc)); if (b==null) return Collections.emptyList();
        // Union, dedup by (owner,name,desc,nsf64), favor canonical
        LinkedHashMap<String,NewRef> dedup = new LinkedHashMap<>();
        for (NewRef r : b) {
            if (r.nsf64 == nsf64) {
                String refKey = r.owner + "\u0000" + r.name + "\u0000" + r.desc + "\u0000" + r.nsf64;
                if (!dedup.containsKey(refKey) || "nsf64".equals(r.bucket)) {
                    dedup.put(refKey, r);
                }
            }
        }
        ArrayList<NewRef> out = new ArrayList<>(dedup.values());
        sort(out); return out;
    }

    public java.util.List<NewRef> near(String owner, String desc, long nsf64, int hammingBudget) {
        ArrayList<NewRef> b = byKey.get(key(owner, desc)); if (b==null) return Collections.emptyList();
        LinkedHashMap<String,NewRef> dedup = new LinkedHashMap<>();
        for (NewRef r : b) {
            int pop = Long.bitCount(r.nsf64 ^ nsf64);
            if (pop <= hammingBudget) {
                String refKey = r.owner + "\u0000" + r.name + "\u0000" + r.desc + "\u0000" + r.nsf64;
                if (!dedup.containsKey(refKey) || "nsf64".equals(r.bucket)) {
                    dedup.put(refKey, r);
                }
            }
        }
        ArrayList<NewRef> out = new ArrayList<>(dedup.values());
        sort(out);
        int MAX = Math.min(512, out.size());
        return new ArrayList<>(out.subList(0, MAX));
    }

    private static void sort(ArrayList<NewRef> xs){
        Collections.sort(xs, new Comparator<NewRef>() {
            public int compare(NewRef a, NewRef b){
                int c = a.owner.compareTo(b.owner); if (c!=0) return c;
                c = a.desc.compareTo(b.desc); if (c!=0) return c;
                return a.name.compareTo(b.name);
            }});
    }

    // Package-private accessor for deterministic diagnostics/tests
    java.util.List<NewRef> getByFp(String owner, String desc, long nsf64) {
        ArrayList<NewRef> b = byKey.get(key(owner, desc)); if (b==null) return Collections.emptyList();
        LinkedHashMap<String,NewRef> dedup = new LinkedHashMap<>();
        for (NewRef r : b) {
            if (r.nsf64 == nsf64) {
                String refKey = r.owner + "\u0000" + r.name + "\u0000" + r.desc + "\u0000" + r.nsf64;
                if (!dedup.containsKey(refKey) || "nsf64".equals(r.bucket)) {
                    dedup.put(refKey, r);
                }
            }
        }
        ArrayList<NewRef> out = new ArrayList<>(dedup.values());
        sort(out);
        return out;
    }
}
// >>> AUTOGEN: BYTECODEMAPPER NSF INDEX END
