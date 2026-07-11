package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClKernelResourceInfoReaderTest {

    @Test
    void readsStandardKernelResourceMetrics() {
        OpenClKernelResourceInfo info = OpenClKernelResourceInfoReader.read((parameter, value) -> {
            long metric = switch (parameter) {
                case CL10.CL_KERNEL_WORK_GROUP_SIZE -> 1_024L;
                case CL11.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE -> 32L;
                case CL10.CL_KERNEL_LOCAL_MEM_SIZE -> 2_048L;
                case CL11.CL_KERNEL_PRIVATE_MEM_SIZE -> 96L;
                default -> throw new AssertionError("Unexpected parameter " + parameter);
            };
            putMetric(value, metric);
            return CL10.CL_SUCCESS;
        });

        assertTrue(info.available());
        assertEquals(1_024L, info.maxWorkGroupSize());
        assertEquals(32L, info.preferredWorkGroupSizeMultiple());
        assertEquals(2_048L, info.localMemoryBytes());
        assertEquals(96L, info.privateMemoryBytes());
        String compilerLog = info.appendToCompilerLog("primary log");
        assertTrue(compilerLog.contains("max work-group size: 1024"));
        assertTrue(compilerLog.contains("private memory: 96 bytes"));
    }

    @Test
    void preservesSupportedMetricsWhenOneQueryFails() {
        OpenClKernelResourceInfo info = OpenClKernelResourceInfoReader.read((parameter, value) -> {
            if (parameter == CL11.CL_KERNEL_PRIVATE_MEM_SIZE) {
                return CL10.CL_INVALID_VALUE;
            }
            putMetric(value, parameter == CL10.CL_KERNEL_LOCAL_MEM_SIZE ? 512L : 64L);
            return CL10.CL_SUCCESS;
        });

        assertTrue(info.available());
        assertEquals(512L, info.localMemoryBytes());
        assertEquals(OpenClKernelResourceInfo.UNKNOWN, info.privateMemoryBytes());
        assertFalse(info.appendToCompilerLog("").contains("private memory:"));
    }

    @Test
    void returnsUnavailableWhenDriverRejectsEveryQuery() {
        OpenClKernelResourceInfo info = OpenClKernelResourceInfoReader.read(
                (parameter, value) -> CL10.CL_INVALID_VALUE
        );

        assertFalse(info.available());
        assertEquals("primary log", info.appendToCompilerLog("primary log"));
    }

    @Test
    void isolatesUnexpectedQueryFailuresPerMetric() {
        OpenClKernelResourceInfo info = OpenClKernelResourceInfoReader.read((parameter, value) -> {
            throw new IllegalStateException("synthetic driver failure");
        });

        assertFalse(info.available());
    }

    private static void putMetric(ByteBuffer value, long metric) {
        if (value.capacity() == Integer.BYTES) {
            value.putInt(0, (int) metric);
        } else {
            value.putLong(0, metric);
        }
    }
}
