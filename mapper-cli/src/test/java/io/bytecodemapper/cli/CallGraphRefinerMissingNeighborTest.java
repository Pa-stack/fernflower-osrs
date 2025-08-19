// >>> AUTOGEN: BYTECODEMAPPER TEST CallGraphRefinerMissingNeighborTest BEGIN
package io.bytecodemapper.cli;

import io.bytecodemapper.cli.method.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class CallGraphRefinerMissingNeighborTest {

    @Test
    public void handlesNeighborWithoutCandidateSet() {
        MethodRef u1 = new MethodRef("A","m1","()V");
        MethodRef u2 = new MethodRef("A","m2","()V"); // will appear in graph, but NOT in candidates
        MethodRef v1 = new MethodRef("B","m1","()V");
        MethodRef v2 = new MethodRef("B","m2","()V");

        MethodFeatures mfv1 = new MethodFeatures(v1,0L,new java.util.BitSet(),false,false,new int[200],java.util.Collections.<String>emptyList(),java.util.Collections.<String>emptyList());
        MethodFeatures mfv2 = new MethodFeatures(v2,0L,new java.util.BitSet(),false,false,new int[200],java.util.Collections.<String>emptyList(),java.util.Collections.<String>emptyList());

        Map<MethodRef, CallGraphRefiner.CandidateSet> cand = new LinkedHashMap<MethodRef, CallGraphRefiner.CandidateSet>();
        // Only u1 has candidates (u2 is missing to simulate filtered/abstract/native cases)
        cand.put(u1, new CallGraphRefiner.CandidateSet(java.util.Arrays.asList(mfv1, mfv2), new double[]{0.60, 0.59}));

        Map<MethodRef, Set<MethodRef>> adjOld = new LinkedHashMap<MethodRef, Set<MethodRef>>();
        Map<MethodRef, Set<MethodRef>> adjNew = new LinkedHashMap<MethodRef, Set<MethodRef>>();
        adjOld.put(u1, new java.util.LinkedHashSet<MethodRef>(java.util.Arrays.asList(u2)));
        adjOld.put(u2, java.util.Collections.<MethodRef>emptySet()); // present in graph, but no candidates
        adjNew.put(v1, new java.util.LinkedHashSet<MethodRef>(java.util.Arrays.asList(v2)));
        adjNew.put(v2, new java.util.LinkedHashSet<MethodRef>(java.util.Arrays.asList(v1)));

        CallGraphRefiner.Result r = CallGraphRefiner.refine(cand, adjOld, adjNew, 0.70, 3);
        // Should not throw, should return a mapping for u1
        assertEquals(1, r.mapping.size());
        assertTrue(r.mapping.containsKey(u1));
    }
}
// <<< AUTOGEN: BYTECODEMAPPER TEST CallGraphRefinerMissingNeighborTest END
