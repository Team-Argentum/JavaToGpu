package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaImageSamplerAbiTest {

    @Test
    void descriptorsCoverAllCurrentImageAndSamplerWrappers() {
        assertEquals(17, CudaImageSamplerAbi.descriptors().size());
        assertTrue(CudaImageSamplerAbi.descriptors().stream().allMatch(CudaImageSamplerAbi.Descriptor::ready));

        assertDescriptor(Image1DReadOnly.class.getName(), "image1d-read-only", "read-texture-object", "CUtexObject");
        assertDescriptor(Image2DReadOnly.class.getName(), "image2d-read-only", "read-texture-object", "CUtexObject");
        assertDescriptor(Image2DWriteOnly.class.getName(), "image2d-write-only", "write-surface-object", "CUsurfObject");
        assertDescriptor(Image3DWriteOnly.class.getName(), "image3d-write-only", "write-surface-object", "CUsurfObject");
        assertDescriptor(Sampler.class.getName(), "sampler", "texture-descriptor-state", "folded-into-CUDA_TEXTURE_DESC");

        assertEquals(2, CudaImageSamplerAbi.descriptors().stream()
                .filter(CudaImageSamplerAbi.Descriptor::sourcePreviewEnabled)
                .count());
        assertEquals(1, CudaImageSamplerAbi.descriptors().stream()
                .filter(CudaImageSamplerAbi.Descriptor::sourcePreviewFolded)
                .count());
        assertEquals(6, CudaImageSamplerAbi.descriptors().stream()
                .mapToInt(CudaImageSamplerAbi.Descriptor::sourcePreviewKernelParameterSlotCount)
                .sum());
        assertEquals(4, CudaImageSamplerAbi.descriptors().stream()
                .mapToInt(CudaImageSamplerAbi.Descriptor::sourcePreviewMetadataSlotCount)
                .sum());
        assertEquals(44, CudaImageSamplerAbi.descriptors().stream()
                .mapToInt(CudaImageSamplerAbi.Descriptor::plannedRuntimeKernelParameterSlotCount)
                .sum());
        assertEquals(28, CudaImageSamplerAbi.descriptors().stream()
                .mapToInt(CudaImageSamplerAbi.Descriptor::plannedRuntimeMetadataSlotCount)
                .sum());
        assertTrue(CudaImageSamplerAbi.descriptors().stream()
                .noneMatch(CudaImageSamplerAbi.Descriptor::runtimeBindingEnabled));
        assertEquals(0, CudaImageSamplerAbi.descriptors().stream()
                .mapToInt(CudaImageSamplerAbi.Descriptor::runtimeBindingKernelParameterSlotCount)
                .sum());
    }

    @Test
    void unsupportedBlockersPreserveCurrentFailClosedNames() {
        CudaImageSamplerAbi.Descriptor image = CudaImageSamplerAbi.descriptorFor(Image2DReadOnly.class.getName()).orElseThrow();
        CudaImageSamplerAbi.Descriptor sampler = CudaImageSamplerAbi.descriptorFor(Sampler.class.getName()).orElseThrow();

        assertEquals(
                "cuda-driver-image-argument-unsupported:0:" + Image2DReadOnly.class.getName(),
                image.unsupportedBlocker(0, Image2DReadOnly.class.getName())
        );
        assertEquals(
                "cuda-driver-sampler-argument-unsupported:1:" + Sampler.class.getName(),
                sampler.unsupportedBlocker(1, Sampler.class.getName())
        );
        assertFalse(image.sampler());
        assertTrue(sampler.sampler());
    }

    private static void assertDescriptor(String javaType, String key, String role, String carrier) {
        CudaImageSamplerAbi.Descriptor descriptor = CudaImageSamplerAbi.descriptorFor(javaType).orElseThrow();

        assertEquals(key, descriptor.key());
        assertEquals(role, descriptor.cudaAbiRole());
        assertEquals(carrier, descriptor.parameterCarrier());
        assertEquals("planned", descriptor.implementationStatus());
        assertFalse(descriptor.productionSupportEnabled());
        assertEquals("fail-closed", descriptor.runtimeBindingStatus());
        assertFalse(descriptor.runtimeBindingEnabled());
        assertEquals(0, descriptor.runtimeBindingKernelParameterSlotCount());
    }
}
