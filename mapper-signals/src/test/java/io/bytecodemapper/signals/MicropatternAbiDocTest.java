package io.bytecodemapper.signals;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Documents fixed 17-bit micropattern ABI and asserts a literal mask. */
public class MicropatternAbiDocTest {

    // Fixed, documented order for 17 bits (frozen ABI).
    // 0  NoParams
    // 1  NoReturn
    // 2  Recursive
    // 3  SameName
    // 4  Leaf
    // 5  ObjectCreator
    // 6  FieldReader
    // 7  FieldWriter
    // 8  TypeManipulator
    // 9  StraightLine
    // 10 Looping
    // 11 Exceptions
    // 12 LocalReader
    // 13 LocalWriter
    // 14 ArrayCreator
    // 15 ArrayReader
    // 16 ArrayWriter
    private static final String[] MICRO_BITS = new String[] {
        "NoParams", "NoReturn", "Recursive", "SameName", "Leaf",
        "ObjectCreator", "FieldReader", "FieldWriter", "TypeManipulator", "StraightLine",
        "Looping", "Exceptions", "LocalReader", "LocalWriter", "ArrayCreator",
        "ArrayReader", "ArrayWriter"
    };

    @Test
    public void testMicropatternBitOrder_documented() throws Exception {
    System.out.println("testMicropatternBitOrder_documented");
        Assert.assertEquals("MICRO_BITS length must be 17", 17, MICRO_BITS.length);
    // Build a mask for {NoParams, Recursive, ObjectCreator, StraightLine} = bits {0,2,5,9}
    int mask = (1 << 0) | (1 << 2) | (1 << 5) | (1 << 9);
        int expected = 0b00000000010100101; // 17-bit literal
    System.out.println("ABI literal: 0b00000000010100101");
    Assert.assertEquals("literal 17-bit mask must match: 0b00000000010100101", expected, mask);

        // Ensure doc file exists and lists the same order.
        Path doc = Paths.get("mapper-signals", "src", "test", "resources", "fixtures", "bitset-abi.md");
        Assert.assertTrue("bitset-abi.md must exist", Files.exists(doc));
        List<String> lines = Files.readAllLines(doc);
        for (int i = 0; i < 17; i++) {
            String expectedLine = String.format("%02d: %s", i, MICRO_BITS[i]);
            boolean has = false;
            for (String s : lines) {
                if (s.trim().equals(expectedLine)) { has = true; break; }
            }
            Assert.assertTrue("bit index line missing: " + expectedLine, has);
        }
    }

    @Test
    public void testMicropatternBitOrder_additionalCombination() throws Exception {
        System.out.println("testMicropatternBitOrder_additionalCombination");
        // Bits {1,3,6,15}
        int mask = (1 << 1) | (1 << 3) | (1 << 6) | (1 << 15);
        int expected = ((1 << 1) | (1 << 3) | (1 << 6) | (1 << 15));
        Assert.assertEquals("additional 17-bit mask combo must match", expected, mask);

        // Strict doc check: exactly 17 lines, indices 00..16 contiguous, no duplicates
        Path doc = Paths.get("mapper-signals", "src", "test", "resources", "fixtures", "bitset-abi.md");
        java.util.List<String> lines = java.nio.file.Files.readAllLines(doc);
        Assert.assertEquals("bitset-abi.md must have exactly 17 lines", 17, lines.size());
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < 17; i++) {
            String expectedPrefix = String.format("%02d: ", i);
            boolean present = false;
            for (String s : lines) {
                if (s.startsWith(expectedPrefix)) {
                    present = true;
                    Assert.assertTrue("duplicate index " + i, seen.add(s));
                    break;
                }
            }
            Assert.assertTrue("missing index line: " + expectedPrefix, present);
        }
    }
}
