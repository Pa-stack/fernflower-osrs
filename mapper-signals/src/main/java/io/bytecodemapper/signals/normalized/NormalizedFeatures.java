// >>> AUTOGEN: BYTECODEMAPPER NSF MODEL BEGIN
package io.bytecodemapper.signals.normalized;

import java.util.*;

public final class NormalizedFeatures {
    public final Map<String,Integer> opcodeBag;       // normalized mnemonic -> count
    public final Map<String,Integer> callKinds;       // VIRT/STATIC/INTERFACE/CTOR -> count
    public final Map<String,Integer> stackDeltaHist;  // e.g. "-2","-1","0","+1","+2" -> count
    public final TryCatchShape tryShape;              // summarized try/catch topology
    public final MinHash32 literalsSketch;            // sketch of numeric immediates (pre-filtered)
    public final TfIdfSketch stringsSketch;           // lightweight TF vector (idf applied later)
    public final long nsf64;                          // stable 64-bit fingerprint

    // CODEGEN-BEGIN: nsfv2-features
    // Derived immutable NSFv2 signal views
    private final LinkedHashMap<String,Integer> stackHist; // fixed order
    private final int tryDepth;
    private final int tryFanout;
    private final int catchTypesHash;
    private final int[] litsMinHash64; // may be null
    private final int[] invokeKindCounts; // length 4: VIRT, STATIC, INTERFACE, CTOR
    // CODEGEN-END: nsfv2-features

    public NormalizedFeatures(Map<String,Integer> opcodeBag, 
                              Map<String,Integer> callKinds,
                              Map<String,Integer> stackDeltaHist,
                              TryCatchShape tryShape,
                              MinHash32 literalsSketch,
                              TfIdfSketch stringsSketch,
                              long nsf64) {
        this.opcodeBag = opcodeBag;
        this.callKinds = callKinds;
        this.stackDeltaHist = stackDeltaHist;
        this.tryShape = tryShape;
        this.literalsSketch = literalsSketch;
        this.stringsSketch = stringsSketch;
        this.nsf64 = nsf64;

        // CODEGEN-BEGIN: nsfv2-features
        // Initialize deterministic NSFv2 views
        final String[] order = new String[]{"-2","-1","0","+1","+2"};
        LinkedHashMap<String,Integer> sh = new LinkedHashMap<String,Integer>();
        for (String k : order) {
            Integer v = (stackDeltaHist == null) ? null : stackDeltaHist.get(k);
            sh.put(k, Integer.valueOf(v == null ? 0 : v.intValue()));
        }
        this.stackHist = sh;
        this.tryDepth = (tryShape == null ? 0 : tryShape.depth);
        this.tryFanout = (tryShape == null ? 0 : tryShape.fanout);
        this.catchTypesHash = (tryShape == null ? 0 : tryShape.catchTypeHash);
        this.litsMinHash64 = (literalsSketch == null ? null : literalsSketch.sketch);
        // Map callKinds to counts in fixed order
        int v = (callKinds == null || callKinds.get("VIRT") == null) ? 0 : callKinds.get("VIRT").intValue();
        int s = (callKinds == null || callKinds.get("STATIC") == null) ? 0 : callKinds.get("STATIC").intValue();
        int i = (callKinds == null || callKinds.get("INTERFACE") == null) ? 0 : callKinds.get("INTERFACE").intValue();
        int c = 0; if (callKinds != null && callKinds.get("CTOR") != null) c = callKinds.get("CTOR").intValue();
        this.invokeKindCounts = new int[]{v, s, i, c};
        // CODEGEN-END: nsfv2-features
    }

    // Minimal inner types to avoid new deps (stubs already used elsewhere)
    public static final class TryCatchShape {
        public final int depth;
        public final int fanout;
        public final int catchTypeHash; // stable hash of sorted catch/internal names
        public TryCatchShape(int depth, int fanout, int catchTypeHash) {
            this.depth = depth; this.fanout = fanout; this.catchTypeHash = catchTypeHash;
        }
    }
    public static final class MinHash32 {
        public final int[] sketch; // fixed length (e.g., 64)
        public MinHash32(int[] s) { this.sketch = s; }
    }
    public static final class TfIdfSketch {
        public final Map<String,Float> tf; // raw TF; IDF applied by scorer
        public TfIdfSketch(Map<String,Float> tf) { this.tf = tf; }
    }

    // CODEGEN-BEGIN: nsfv2-features
    public Map<String,Integer> getStackHist() { return this.stackHist; }
    public int getTryDepth() { return this.tryDepth; }
    public int getTryFanout() { return this.tryFanout; }
    public int getCatchTypesHash() { return this.catchTypesHash; }
    // Nullable avoided to keep Java 8 deps minimal
    public int[] getLitsMinHash64() { return this.litsMinHash64; }
    public int[] getInvokeKindCounts() { return this.invokeKindCounts; }
    public long getNsf64() { return this.nsf64; }
    // CODEGEN-END: nsfv2-features
}
// >>> AUTOGEN: BYTECODEMAPPER NSF MODEL END
