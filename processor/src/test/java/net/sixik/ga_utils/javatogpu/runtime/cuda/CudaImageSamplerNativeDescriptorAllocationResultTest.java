package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryAllocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryAllocationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryService;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryServiceRegistry;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerNativeDescriptorAllocationResultTest {

    @Test
    void explicitNativeDescriptorAllocationOwnsAndReleasesNativeMemory() {
        CudaImageSamplerNativeDescriptorAllocationTransactionPlan plan = allocationTransactionPlan();

        CudaImageSamplerNativeDescriptorAllocationResult result =
                CudaImageSamplerNativeDescriptorAllocationResult.allocate(plan, 128, 64);
        Map<String, String> fields = result.artifactFields("test.cuda.imageSamplerNativeDescriptorAllocationResult");

        assertEquals("ready", result.status());
        assertTrue(result.ready());
        assertEquals("none", result.firstBlocker());
        assertEquals("blocked", result.allocationTransactionPlanStatus());
        assertTrue(result.allocationTransactionPlanPresent());
        assertTrue(result.allocationApplyEnabled());
        assertTrue(result.nativeMemoryAllocationEnabled());
        assertFalse(result.sdkStructByteEncodingEnabled());
        assertTrue(result.cleanupApplyEnabled());
        assertTrue(result.rollbackApplyEnabled());
        assertFalse(result.objectCreationEnabled());
        assertFalse(result.runtimeBindingEnabled());
        assertEquals(3, result.entries().size());
        assertEquals(3, result.entryReadyCount());
        assertEquals(0, result.entryBlockedCount());
        assertEquals(4, result.descriptorOwnerCount());
        assertEquals(2, result.resourceDescriptorOwnerCount());
        assertEquals(2, result.textureDescriptorOwnerCount());
        assertEquals(4, result.activeDescriptorOwnerCount());
        assertEquals(4, result.activeNativeDescriptorCount());
        assertEquals(4, result.nativeAddressPresentCount());
        assertEquals(4, result.allocationEnabledCount());
        assertEquals(4, result.cleanupActiveCount());
        assertEquals(4, result.rollbackActiveCount());
        assertEquals(384, result.nativeByteSize());
        assertEquals("native-memory:lwjgl", result.nativeMemoryServiceSummary());

        CudaImageSamplerNativeDescriptorAllocationResult.Entry texture = result.entries().get(0);
        assertEquals(2, texture.descriptorOwners().size());
        assertEquals(192, texture.nativeByteSize());
        assertAllocated(texture.descriptorOwners().get(0), "resource", "CUDA_RESOURCE_DESC", 128, 0);
        assertAllocated(texture.descriptorOwners().get(1), "texture", "CUDA_TEXTURE_DESC", 64, 1);

        CudaImageSamplerNativeDescriptorAllocationResult.Entry surface = result.entries().get(1);
        assertEquals(1, surface.descriptorOwners().size());
        assertAllocated(surface.descriptorOwners().get(0), "resource", "CUDA_RESOURCE_DESC", 128, 2);

        CudaImageSamplerNativeDescriptorAllocationResult.Entry sampler = result.entries().get(2);
        assertEquals(1, sampler.descriptorOwners().size());
        assertAllocated(sampler.descriptorOwners().get(0), "texture", "CUDA_TEXTURE_DESC", 64, 3);

        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.present"));
        assertEquals("ready", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.status"));
        assertEquals("blocked", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.allocationTransactionPlan.status"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.allocationApply.enabled"));
        assertEquals("true", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.nativeMemoryAllocation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.sdkStructByteEncoding.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.objectCreation.enabled"));
        assertEquals("false", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.runtimeBinding.enabled"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.descriptorOwner.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.resourceDescriptorOwner.count"));
        assertEquals("2", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.textureDescriptorOwner.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.activeDescriptorOwner.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.nativeAddress.present.count"));
        assertEquals("4", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.allocation.enabled.count"));
        assertEquals("384", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.nativeByteSize"));
        assertEquals("128", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.entry.0.descriptorOwner.0.nativeByteSize"));
        assertEquals("64", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.entry.0.descriptorOwner.1.nativeByteSize"));
        assertEquals("native-memory:lwjgl", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.entry.0.descriptorOwner.0.nativeMemory.service.id"));

        result.close();

        assertTrue(result.closed());
        assertEquals("blocked", result.status());
        assertFalse(result.ready());
        assertEquals("cuda-image-sampler-native-descriptor-allocation-result-closed", result.firstBlocker());
        assertEquals(0, result.activeDescriptorOwnerCount());
        assertEquals(0, result.activeNativeDescriptorCount());
        assertEquals(0, result.nativeAddressPresentCount());
        assertEquals(0, result.cleanupActiveCount());
        assertEquals(0, result.rollbackActiveCount());
        assertTrue(texture.descriptorOwners().get(0).closed());
        assertFalse(texture.descriptorOwners().get(0).active());
        assertFalse(texture.descriptorOwners().get(0).nativeAddressPresent());

        result.close();
        assertTrue(result.closed());
    }

    @Test
    void explicitNativeDescriptorAllocationCanUseInjectedNativeMemoryService() {
        AtomicInteger closeCount = new AtomicInteger();
        GpuRuntimeNativeMemoryServiceRegistry registry = GpuRuntimeNativeMemoryServiceRegistry.of(List.of(
                new SyntheticNativeMemoryService(closeCount)
        ));

        CudaImageSamplerNativeDescriptorAllocationResult result =
                CudaImageSamplerNativeDescriptorAllocationResult.allocate(allocationTransactionPlan(), 128, 64, registry);
        Map<String, String> fields = result.artifactFields("test.cuda.imageSamplerNativeDescriptorAllocationResult");

        assertEquals("ready", result.status());
        assertEquals(4, result.descriptorOwnerCount());
        assertEquals(4, result.nativeAddressPresentCount());
        assertEquals("native-memory:synthetic", result.nativeMemoryServiceSummary());
        assertEquals("native-memory:synthetic", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.entry.0.descriptorOwner.0.nativeMemory.service.id"));
        assertEquals("native-memory:synthetic", fields.get("runtime.cuda.imageSamplerNativeDescriptorAllocationResult.entry.0.descriptorOwner.1.nativeMemory.service.id"));

        result.close();

        assertEquals(4, closeCount.get());
        assertEquals(0, result.nativeAddressPresentCount());
        assertEquals("blocked", result.status());
    }

    private static CudaImageSamplerNativeDescriptorAllocationTransactionPlan allocationTransactionPlan() {
        return CudaImageSamplerNativeDescriptorAllocationTransactionPlan.from(
                CudaImageSamplerNativeDescriptorAllocationPreflight.from(
                        CudaImageSamplerNativeDescriptorEncodingPlan.from(
                                CudaImageSamplerDescriptorPayloadModel.from(
                                        CudaImageSamplerDescriptorBuildPlan.from(
                                                List.of(
                                                        new GpuKernelParameterDescriptor("inputImage", Image2DReadOnly.class.getName(), GpuKernelParameterAccess.READ_ONLY),
                                                        new GpuKernelParameterDescriptor("outputImage", Image2DWriteOnly.class.getName(), GpuKernelParameterAccess.READ_WRITE),
                                                        new GpuKernelParameterDescriptor("sampler", Sampler.class.getName(), GpuKernelParameterAccess.VALUE)
                                                ),
                                                new Object[]{
                                                        Image2DReadOnly.borrowed(0xCAFE_7A01L, 8, 4),
                                                        Image2DWriteOnly.borrowed(0xCAFE_7A02L, 8, 4),
                                                        Sampler.borrowed(0xCAFE_7A03L)
                                                }
                                        )
                                )
                        )
                )
        );
    }

    private static void assertAllocated(
            CudaDriverImageSamplerNativeDescriptor owner,
            String descriptorKind,
            String structName,
            int nativeByteSize,
            int cleanupOrder
    ) {
        assertEquals(descriptorKind, owner.descriptorKind());
        assertEquals(structName, owner.structName());
        assertEquals(nativeByteSize, owner.nativeByteSize());
        assertEquals(cleanupOrder, owner.cleanupOrder());
        assertEquals(cleanupOrder, owner.rollbackOrder());
        assertTrue(owner.nativeAddressPresent());
        assertTrue(owner.allocationEnabled());
        assertTrue(owner.active());
        assertFalse(owner.closed());
        assertEquals("active", owner.ownershipStatus());
        assertEquals("active", owner.cleanupStatus());
        assertEquals("active", owner.rollbackStatus());
    }

    private static final class SyntheticNativeMemoryService implements GpuRuntimeNativeMemoryService {
        private final AtomicInteger closeCount;
        private long nextAddress = 0xCAFE_0000L;

        private SyntheticNativeMemoryService(AtomicInteger closeCount) {
            this.closeCount = closeCount;
        }

        @Override
        public String serviceId() {
            return "native-memory:synthetic";
        }

        @Override
        public int serviceOrder() {
            return 1;
        }

        @Override
        public GpuRuntimeNativeMemoryAllocation allocate(GpuRuntimeNativeMemoryAllocationRequest request) {
            nextAddress += 0x100L;
            return new GpuRuntimeNativeMemoryAllocation(
                    serviceId(),
                    "1",
                    request,
                    ByteBuffer.allocateDirect(request.byteSize()),
                    nextAddress,
                    closeCount::incrementAndGet
            );
        }
    }
}
