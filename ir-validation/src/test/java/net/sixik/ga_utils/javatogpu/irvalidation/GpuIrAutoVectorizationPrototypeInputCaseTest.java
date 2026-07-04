package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuIrAutoVectorizationPrototypeInputCaseTest {
    @Test
    void defensivelyCopiesInputArrays() {
        int[] source = new int[]{1, 2, 3, 4};
        GpuIrAutoVectorizationPrototypeInputCase inputCase = new GpuIrAutoVectorizationPrototypeInputCase(
                "case-a",
                Map.of("out", source)
        );

        source[0] = 99;
        int[] returned = inputCase.array("out");
        returned[1] = 88;
        inputCase.arrays().get("out")[2] = 77;

        assertArrayEquals(new int[]{1, 2, 3, 4}, inputCase.array("out"));
    }

    @Test
    void rejectsInvalidInputCaseMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPrototypeInputCase("", Map.of("out", new int[]{1})));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPrototypeInputCase("case-a", Map.of()));

        Map<String, int[]> blankName = new LinkedHashMap<>();
        blankName.put("", new int[]{1});
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPrototypeInputCase("case-a", blankName));

        Map<String, int[]> nullArray = new LinkedHashMap<>();
        nullArray.put("out", null);
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationPrototypeInputCase("case-a", nullArray));
    }
}
