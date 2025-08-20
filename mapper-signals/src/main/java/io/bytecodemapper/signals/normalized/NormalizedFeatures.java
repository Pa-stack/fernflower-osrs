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
}
// >>> AUTOGEN: BYTECODEMAPPER NSF MODEL END
