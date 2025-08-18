// >>> AUTOGEN: BYTECODEMAPPER GreedyAssign BEGIN
package io.bytecodemapper.core.assign;

import java.util.*;

public final class GreedyAssign {
    private GreedyAssign(){}

    /** Greedy max assignment from a score matrix (deterministic: stable sorts). */
    public static List<int[]> assign(double[][] score) {
        int n = score.length, m = score[0].length;
        boolean[] usedR = new boolean[n], usedC = new boolean[m];
        List<int[]> pairs = new ArrayList<int[]>();
        List<int[]> all = new ArrayList<int[]>();
        for (int r=0;r<n;r++) for (int c=0;c<m;c++) {
            all.add(new int[]{r,c, Double.valueOf(score[r][c]).hashCode()}); // stable tie break
        }
        Collections.sort(all, new Comparator<int[]>() {
            public int compare(int[] a, int[] b) {
                int cmp = Double.compare(score[b[0]][b[1]], score[a[0]][a[1]]);
                if (cmp != 0) return cmp;
                // deterministic fallback
                if (a[0]!=b[0]) return Integer.compare(a[0], b[0]);
                return Integer.compare(a[1], b[1]);
            }
        });
        for (int[] t : all) {
            int r = t[0], c = t[1];
            if (!usedR[r] && !usedC[c]) {
                usedR[r]=usedC[c]=true;
                pairs.add(new int[]{r,c});
            }
        }
        return pairs;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER GreedyAssign END
