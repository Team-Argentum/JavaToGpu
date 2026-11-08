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
            assertEquals(1, Integer.bitCount(bit), name + " - ровно один бит");
            assertEquals(0, all & bit, name + " не пересекается с остальными");
            assertEquals(name, Features.describe(bit), "описание обратно имени");
            all |= bit;
        }
    }

    @Test
    void unknownNamesRequireNothing() {
        assertEquals(0, Features.fromName("SIMD"));
        assertEquals(0, Features.fromName(""));
        assertEquals(0, Features.fromName("fp64"), "имена чувствительны к регистру, как в аннотации");
    }

    @Test
    void describeJoinsBitsAndMarksEmptyMask() {
        assertEquals("-", Features.describe(0));
        assertEquals("FP64|INT64_ATOMICS", Features.describe(Features.FP64 | Features.INT64_ATOMICS));
    }
}
