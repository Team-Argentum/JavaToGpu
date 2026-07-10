package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenClRuntimeDeviceSelfTestRunnerTest {

    @Test
    void acceptsExpectedReadbackAndReportsFirstMismatch() {
        IntBuffer valid = IntBuffer.allocate(64);
        for (int index = 0; index < valid.capacity(); index++) {
            valid.put(index, index * 31 + 7);
        }
        assertNull(OpenClRuntimeDeviceSelfTestRunner.firstMismatch(valid));

        valid.put(17, -1);
        assertEquals(
                "OpenCL correctness smoke mismatch at index 17: expected 534, got -1",
                OpenClRuntimeDeviceSelfTestRunner.firstMismatch(valid)
        );
    }
}
