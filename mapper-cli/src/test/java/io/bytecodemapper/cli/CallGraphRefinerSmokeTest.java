// >>> AUTOGEN: BYTECODEMAPPER TEST CallGraphRefinerSmokeTest BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class CallGraphRefinerSmokeTest {

    @Test
    public void oscillationDecreasesAndCapsHold() {
        // Two old methods u1,u2; two new v1,v2. Base scores nearly tied crosswise.
        MethodRef u1 = new MethodRef("A","m1","()V");
        MethodRef u2 = new MethodRef("A","m2","()V");
        MethodRef v1 = new MethodRef("B","m1","()V");
        MethodRef v2 = new MethodRef("B","m2","()V");

    // >>> AUTOGEN: BYTECODEMAPPER TEST update MethodFeatures ctor BEGIN
    MethodFeatures mfv1 = new MethodFeatures(v1,0L,new java.util.BitSet(),false,false,new int[200],
        java.util.Collections.<String>emptyList(),java.util.Collections.<String>emptyList(),
        v1.desc, "");
    MethodFeatures mfv2 = new MethodFeatures(v2,0L,new java.util.BitSet(),false,false,new int[200],
        java.util.Collections.<String>emptyList(),java.util.Collections.<String>emptyList(),
        v2.desc, "");
    // <<< AUTOGEN: BYTECODEMAPPER TEST update MethodFeatures ctor END

        Map<MethodRef, CallGraphRefiner.CandidateSet> cand = new LinkedHashMap<MethodRef, CallGraphRefiner.CandidateSet>();
        cand.put(u1, new CallGraphRefiner.CandidateSet(java.util.Arrays.asList(mfv1, mfv2), new double[]{0.60, 0.59}));
        cand.put(u2, new CallGraphRefiner.CandidateSet(java.util.Arrays.asList(mfv1, mfv2), new double[]{0.59, 0.60}));

        Map<MethodRef, Set<MethodRef>> adjOld = new LinkedHashMap<MethodRef, Set<MethodRef>>();
        Map<MethodRef, Set<MethodRef>> adjNew = new LinkedHashMap<MethodRef, Set<MethodRef>>();
        adjOld.put(u1, new java.util.LinkedHashSet<MethodRef>(java.util.Arrays.asList(u2)));
        adjOld.put(u2, new java.util.LinkedHashSet<MethodRef>(java.util.Arrays.asList(u1)));
        adjNew.put(v1, new java.util.LinkedHashSet<MethodRef>(java.util.Arrays.asList(v2)));
        adjNew.put(v2, new java.util.LinkedHashSet<MethodRef>(java.util.Arrays.asList(v1)));

        CallGraphRefiner.Result r = CallGraphRefiner.refine(cand, adjOld, adjNew, 0.70, 5);

        // Mapping should align with neighbors
        assertEquals(v1, r.mapping.get(u1));
        assertEquals(v2, r.mapping.get(u2));

        // Oscillation trend: flips should drop or hit zero quickly
        int[] flips = r.stats.flipsPerIter;
        assertTrue("have at least one iteration", flips.length >= 1);
        if (flips.length >= 2) {
            assertTrue("oscillation should not increase", flips[1] <= flips[0]);
        }

        // Caps: refined scores should be within [-0.05, +0.10] of base
        double s1 = r.bestScore.get(u1);
        double s2 = r.bestScore.get(u2);
        assertTrue(s1 >= 0.60 - 0.05 && s1 <= 0.60 + 0.10);
        assertTrue(s2 >= 0.60 - 0.05 && s2 <= 0.60 + 0.10);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST CallGraphRefinerSmokeTest END
