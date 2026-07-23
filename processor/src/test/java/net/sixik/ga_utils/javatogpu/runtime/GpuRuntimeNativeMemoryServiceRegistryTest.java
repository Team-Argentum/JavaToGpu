package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeNativeMemoryServiceRegistryTest {

    @Test
    void selectsFirstSupportingNativeMemoryServiceInDeterministicOrder() {
        AtomicBoolean closed = new AtomicBoolean(false);
        GpuRuntimeNativeMemoryServiceRegistry registry = GpuRuntimeNativeMemoryServiceRegistry.of(List.of(
                new SyntheticNativeMemoryService("native-memory:late", 20, 0x2000L, closed),
                new SyntheticNativeMemoryService("native-memory:first", 10, 0x1000L, closed)
        ));

        GpuRuntimeNativeMemoryAllocation allocation = registry.allocate(
                new GpuRuntimeNativeMemoryAllocationRequest(
                        GpuBackendTarget.CUDA,
                        "test-allocation",
                        32,
                        8,
                        true
                )
        );

        assertEquals("native-memory:first", allocation.serviceId());
        assertEquals(0x1000L, allocation.nativeAddress());
        assertEquals(32, allocation.nativeByteSize());
        assertEquals("CUDA", allocation.artifactFields("test.nativeMemory")
                .get("test.nativeMemory.request.backend"));

        allocation.close();
        assertTrue(closed.get());
        assertEquals(0L, allocation.nativeAddress());
    }

    @Test
    void rejectsDuplicateNativeMemoryServiceIds() {
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeNativeMemoryServiceRegistry.of(List.of(
                new SyntheticNativeMemoryService("native-memory:duplicate", 10, 0x1000L, new AtomicBoolean(false)),
                new SyntheticNativeMemoryService("native-memory:duplicate", 20, 0x2000L, new AtomicBoolean(false))
        )));
    }

    private record SyntheticNativeMemoryService(
            String serviceId,
            int serviceOrder,
            long nativeAddress,
            AtomicBoolean closed
    ) implements GpuRuntimeNativeMemoryService {

        @Override
        public GpuRuntimeNativeMemoryAllocation allocate(GpuRuntimeNativeMemoryAllocationRequest request) {
            return new GpuRuntimeNativeMemoryAllocation(
                    serviceId,
                    "1",
                    request,
                    ByteBuffer.allocateDirect(request.byteSize()),
                    nativeAddress,
                    () -> closed.set(true)
            );
        }
    }
}
