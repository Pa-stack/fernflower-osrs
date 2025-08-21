// >>> AUTOGEN: BYTECODEMAPPER TEST NsfIndex BEGIN
package io.bytecodemapper.core.index;

import org.junit.Test;
import static org.junit.Assert.*;

public class NsfIndexTest {

    @Test public void canonicalExactAndNear() {
        NsfIndex idx = new NsfIndex();
        String owner = "A"; String desc = "(I)I";
        long fp1 = 0b1011L;           // 11
        long fp2 = fp1 ^ 0x1L;         // differ by 1 bit
    idx.add(owner, desc, "m1", fp1, io.bytecodemapper.core.index.NsfIndex.Mode.CANONICAL);
    idx.add(owner, desc, "m2", fp2, io.bytecodemapper.core.index.NsfIndex.Mode.CANONICAL);

        java.util.List<NsfIndex.NewRef> exact = idx.exact(owner, desc, fp1);
        assertEquals(1, exact.size());
        assertEquals("m1", exact.get(0).name);

        java.util.List<NsfIndex.NewRef> near1 = idx.near(owner, desc, fp1, 1);
        assertEquals(2, near1.size());
        // stable owner/desc/name sort; names m1 then m2
        assertEquals("m1", near1.get(0).name);
        assertEquals("m2", near1.get(1).name);
    }

    @Test public void deterministicOrdering() {
        NsfIndex idx = new NsfIndex();
        String owner = "A"; String desc = "(I)I";
        // multiple entries in deterministic name order
        long base = 0x55AA55AA55AAL;
    for (int i=0;i<6;i++) idx.add(owner, desc, "m"+i, base + i, io.bytecodemapper.core.index.NsfIndex.Mode.CANONICAL);
        java.util.List<NsfIndex.NewRef> a = idx.near(owner, desc, base, 64);
        java.util.List<NsfIndex.NewRef> b = idx.near(owner, desc, base, 64);
        String ja = join(a); String jb = join(b);
        assertEquals(ja, jb);
    }

    // Helper
    private static String join(java.util.List<NsfIndex.NewRef> xs){
        StringBuilder sb = new StringBuilder();
        for (NsfIndex.NewRef r : xs) sb.append(r.owner).append('#').append(r.name).append(r.desc).append('|');
        return sb.toString();
    }
}
// >>> AUTOGEN: BYTECODEMAPPER TEST NsfIndex END
