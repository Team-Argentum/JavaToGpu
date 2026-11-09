package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** {@link Features}: имена возможностей из {@code required} и их читаемое описание. */
class FeaturesTest {

    @Test
    void everyKnownNameMapsToItsOwnBit() {
        int all = 0;
        for (String name : new String[]{"FP64", "INT64_ATOMICS", "INT64_EXT_ATOMICS", "FP16", "IMAGES"}) {
            int bit = Features.fromName(name);
            assertNotEquals(0, bit, name);
            assertEquals(1, Integer.bitCount(bit), name + " is exactly one bit");
            assertEquals(0, all & bit, name + " does not overlap with the others");
            assertEquals(name, Features.describe(bit), "the description is the inverse of the name");
            all |= bit;
        }
    }

    @Test
    void unknownNamesRequireNothing() {
        assertEquals(0, Features.fromName("SIMD"));
        assertEquals(0, Features.fromName(""));
        assertEquals(0, Features.fromName("fp64"), "names are case-sensitive, as in the annotation");
    }

    @Test
    void describeJoinsBitsAndMarksEmptyMask() {
        assertEquals("-", Features.describe(0));
        assertEquals("FP64|INT64_ATOMICS", Features.describe(Features.FP64 | Features.INT64_ATOMICS));
    }
}
