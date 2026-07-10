package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenClProgramBuildLogReaderTest {

    @Test
    void readsNullTerminatedUtf8BuildLog() {
        byte[] buildLog = "Used 32 registers, 0 bytes spill stores\n\0"
                .getBytes(StandardCharsets.UTF_8);

        String result = OpenClProgramBuildLogReader.read((value, size) -> {
            size.put(0, buildLog.length);
            if (value != null) {
                for (int index = 0; index < buildLog.length; index++) {
                    value.put(index, buildLog[index]);
                }
            }
            return CL10.CL_SUCCESS;
        });

        assertEquals("Used 32 registers, 0 bytes spill stores", result);
    }

    @Test
    void returnsEmptyWhenDriverRejectsBuildInfoQuery() {
        String result = OpenClProgramBuildLogReader.read((value, size) -> CL10.CL_INVALID_VALUE);

        assertEquals("", result);
    }

    @Test
    void isolatesUnexpectedBuildInfoFailures() {
        String result = OpenClProgramBuildLogReader.read((value, size) -> {
            throw new IllegalStateException("synthetic driver failure");
        });

        assertEquals("", result);
    }
}
