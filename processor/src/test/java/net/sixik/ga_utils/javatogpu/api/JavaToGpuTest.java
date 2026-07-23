package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaToGpuTest {

    @Test
    void gpuScopeWrapsRuntimeScopeAndRestoresPreviousBackend() {
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntimeBackend scopedBackend = invocation -> {
        };

        GpuScope scope = GpuScope.wrap(GpuRuntime.useBackend(scopedBackend));
        assertSame(scopedBackend, GpuRuntime.backend());
        assertFalse(scope.closed());
        assertFalse(scope.ownsRuntime());

        scope.close();

        assertTrue(scope.closed());
        assertSame(previousBackend, GpuRuntime.backend());
    }

    @Test
    void launchHelpersDelegateToRuntimeExecutionConfigFactories() {
        GpuExecutionConfig oneDimensional = JavaToGpu.launch1D(128L, 64L);
        assertEquals(1, oneDimensional.dimensions());
        assertEquals("128", oneDimensional.globalShape());
        assertEquals("64", oneDimensional.localShape());

        GpuExecutionConfig twoDimensional = JavaToGpu.launch2D(16L, 8L);
        assertEquals(2, twoDimensional.dimensions());
        assertEquals("16x8", twoDimensional.globalShape());
        assertEquals("auto", twoDimensional.localShape());

        GpuExecutionConfig threeDimensional = JavaToGpu.launch3D(4L, 5L, 6L, 2L, 1L, 3L);
        assertEquals(3, threeDimensional.dimensions());
        assertEquals("4x5x6", threeDimensional.globalShape());
        assertEquals("2x1x3", threeDimensional.localShape());
    }
}
