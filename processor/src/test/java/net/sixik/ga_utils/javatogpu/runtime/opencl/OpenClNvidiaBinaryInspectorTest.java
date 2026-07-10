package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClNvidiaBinaryInspectorTest {

    @Test
    void parsesCuobjdumpResourceUsageIntoGenericCompilerFeedbackText() {
        OpenClNvidiaBinaryInspector.Result result = OpenClNvidiaBinaryInspector.parse(
                "cuobjdump",
                Path.of("C:/CUDA/bin/cuobjdump.exe"),
                "Function : kernel\n REG:44 STACK:16 SHARED:0 LOCAL:0\n8 bytes spill stores, 4 bytes spill loads",
                "inspection tool exited with code 0"
        );

        assertEquals("recorded", result.status());
        assertEquals(44, result.registers());
        assertEquals(8, result.spillStoreBytes());
        assertEquals(4, result.spillLoadBytes());
        assertTrue(result.normalizedFeedback().contains("Used 44 registers"));
        assertTrue(result.normalizedFeedback().contains("16 bytes stack frame"));
    }

    @Test
    void parsesPtxasVerboseOutput() {
        OpenClNvidiaBinaryInspector.Result result = OpenClNvidiaBinaryInspector.parse(
                "ptxas",
                Path.of("C:/CUDA/bin/ptxas.exe"),
                "ptxas         .     0 bytes stack frame, 0 bytes spill stores, 0 bytes spill loads\n"
                        + "ptxas info    : Used 38 registers, used 0 barriers",
                "inspection tool exited with code 0"
        );

        assertEquals(38, result.registers());
        assertEquals(0, result.spillStoreBytes());
        assertEquals(0, result.spillLoadBytes());
        assertTrue(result.normalizedFeedback().contains("Used 38 registers"));
    }
}
