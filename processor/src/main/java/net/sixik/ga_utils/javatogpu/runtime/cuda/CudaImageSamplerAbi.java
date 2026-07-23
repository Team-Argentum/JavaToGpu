package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.List;
import java.util.Optional;

/**
 * Internal CUDA image/sampler ABI vocabulary shared by planning reports and fail-closed preflight checks.
 */
final class CudaImageSamplerAbi {

    private static final String IMAGE_API = "net.sixik.ga_utils.javatogpu.api.images.";

    private static final List<Descriptor> DESCRIPTORS = List.of(
            texture("image1d-read-only", "Image1DReadOnly", "read_only image1d_t", "cuda-array-1d"),
            surface("image1d-write-only", "Image1DWriteOnly", "write_only image1d_t", "cuda-array-1d"),
            texture("image1d-array-read-only", "Image1DArrayReadOnly", "read_only image1d_array_t", "cuda-array-layered-1d"),
            surface("image1d-array-write-only", "Image1DArrayWriteOnly", "write_only image1d_array_t", "cuda-array-layered-1d"),
            texture("image1d-buffer-read-only", "Image1DBufferReadOnly", "read_only image1d_buffer_t", "cuda-linear-memory"),
            surface("image1d-buffer-write-only", "Image1DBufferWriteOnly", "write_only image1d_buffer_t", "cuda-linear-memory-or-staged-array"),
            texture("image2d-read-only", "Image2DReadOnly", "read_only image2d_t", "cuda-array-2d"),
            surface("image2d-write-only", "Image2DWriteOnly", "write_only image2d_t", "cuda-array-2d"),
            texture("image2d-mipmapped-read-only", "Image2DMipmappedReadOnly", "read_only image2d_t", "cuda-mipmapped-array-2d"),
            surface("image2d-mipmapped-write-only", "Image2DMipmappedWriteOnly", "write_only image2d_t", "cuda-mipmapped-array-2d"),
            texture("image2d-msaa-read-only", "Image2DMsaaReadOnly", "read_only image2d_msaa_t", "cuda-array-2d-msaa"),
            surface("image2d-msaa-write-only", "Image2DMsaaWriteOnly", "write_only image2d_msaa_t", "cuda-array-2d-msaa"),
            texture("image2d-array-read-only", "Image2DArrayReadOnly", "read_only image2d_array_t", "cuda-array-layered-2d"),
            surface("image2d-array-write-only", "Image2DArrayWriteOnly", "write_only image2d_array_t", "cuda-array-layered-2d"),
            texture("image3d-read-only", "Image3DReadOnly", "read_only image3d_t", "cuda-array-3d"),
            surface("image3d-write-only", "Image3DWriteOnly", "write_only image3d_t", "cuda-array-3d"),
            sampler()
    );

    private CudaImageSamplerAbi() {
    }

    static List<Descriptor> descriptors() {
        return DESCRIPTORS;
    }

    static Optional<Descriptor> descriptorFor(String javaType) {
        String simpleName = GpuTypeSupport.simpleTypeName(GpuTypeSupport.declaredType(javaType));
        return DESCRIPTORS.stream()
                .filter(descriptor -> descriptor.javaSimpleName().equals(simpleName))
                .findFirst();
    }

    record Descriptor(
            String key,
            String javaSimpleName,
            String openClType,
            String cudaAbiRole,
            String cudaResourceKind,
            String parameterCarrier,
            String requiredDriverSymbols,
            String implementationStatus,
            boolean productionSupportEnabled
    ) {
        Descriptor {
            key = normalize(key, "unknown");
            javaSimpleName = normalize(javaSimpleName, "unknown");
            openClType = normalize(openClType, "unknown");
            cudaAbiRole = normalize(cudaAbiRole, "unknown");
            cudaResourceKind = normalize(cudaResourceKind, "unknown");
            parameterCarrier = normalize(parameterCarrier, "unknown");
            requiredDriverSymbols = normalize(requiredDriverSymbols, "none");
            implementationStatus = normalize(implementationStatus, "planned");
        }

        String javaQualifiedName() {
            return IMAGE_API + javaSimpleName;
        }

        boolean sampler() {
            return "sampler_t".equals(openClType);
        }

        boolean readTextureObject() {
            return "read-texture-object".equals(cudaAbiRole);
        }

        boolean writeSurfaceObject() {
            return "write-surface-object".equals(cudaAbiRole);
        }

        boolean textureDescriptorState() {
            return "texture-descriptor-state".equals(cudaAbiRole);
        }

        boolean cudaArray2D() {
            return "cuda-array-2d".equals(cudaResourceKind);
        }

        boolean sourcePreviewEnabled() {
            return cudaArray2D() && (readTextureObject() || writeSurfaceObject());
        }

        boolean sourcePreviewFolded() {
            return sampler();
        }

        String sourcePreviewStatus() {
            if (sourcePreviewEnabled()) {
                return "enabled";
            }
            if (sourcePreviewFolded()) {
                return "folded";
            }
            return "pending";
        }

        String sourcePreviewCarrier() {
            if (readTextureObject() && sourcePreviewEnabled()) {
                return "cudaTextureObject_t";
            }
            if (writeSurfaceObject() && sourcePreviewEnabled()) {
                return "cudaSurfaceObject_t";
            }
            if (sourcePreviewFolded()) {
                return "omitted-from-kernel-signature";
            }
            return "pending";
        }

        int sourcePreviewMetadataSlotCount() {
            return sourcePreviewEnabled() && cudaArray2D() ? 2 : 0;
        }

        int sourcePreviewKernelParameterSlotCount() {
            return sourcePreviewEnabled() ? 1 + sourcePreviewMetadataSlotCount() : 0;
        }

        int plannedRuntimeMetadataSlotCount() {
            if (sampler()) {
                return 0;
            }
            if (cudaResourceKind.contains("3d")) {
                return 3;
            }
            if (cudaResourceKind.contains("2d")) {
                return 2;
            }
            if (cudaResourceKind.contains("1d") || cudaResourceKind.contains("linear")) {
                return 1;
            }
            return 0;
        }

        int plannedRuntimeKernelParameterSlotCount() {
            return sampler() ? 0 : 1 + plannedRuntimeMetadataSlotCount();
        }

        int runtimeBindingKernelParameterSlotCount() {
            return runtimeBindingEnabled() ? plannedRuntimeKernelParameterSlotCount() : 0;
        }

        String runtimeBindingStatus() {
            return "fail-closed";
        }

        boolean runtimeBindingEnabled() {
            return false;
        }

        boolean ready() {
            return !"unknown".equals(javaSimpleName)
                    && !"unknown".equals(openClType)
                    && !"unknown".equals(cudaAbiRole)
                    && !"unknown".equals(cudaResourceKind)
                    && !"unknown".equals(parameterCarrier)
                    && "planned".equals(implementationStatus)
                    && !productionSupportEnabled;
        }

        String unsupportedBlocker(int parameterIndex, String actualJavaType) {
            String kind = sampler() ? "sampler" : "image";
            return "cuda-driver-" + kind + "-argument-unsupported:" + parameterIndex + ':' + actualJavaType;
        }
    }

    private static Descriptor texture(String key, String simpleName, String openClType, String resourceKind) {
        return new Descriptor(
                key,
                simpleName,
                openClType,
                "read-texture-object",
                resourceKind,
                "CUtexObject",
                textureSymbols(resourceKind),
                "planned",
                false
        );
    }

    private static Descriptor surface(String key, String simpleName, String openClType, String resourceKind) {
        return new Descriptor(
                key,
                simpleName,
                openClType,
                "write-surface-object",
                resourceKind,
                "CUsurfObject",
                surfaceSymbols(resourceKind),
                "planned",
                false
        );
    }

    private static Descriptor sampler() {
        return new Descriptor(
                "sampler",
                "Sampler",
                "sampler_t",
                "texture-descriptor-state",
                "sampler-state",
                "folded-into-CUDA_TEXTURE_DESC",
                "cuTexObjectCreate,cuTexObjectDestroy",
                "planned",
                false
        );
    }

    private static String textureSymbols(String resourceKind) {
        if (resourceKind.contains("mipmapped")) {
            return "cuMipmappedArrayCreate,cuMipmappedArrayGetLevel,cuTexObjectCreate,cuTexObjectDestroy,cuMipmappedArrayDestroy";
        }
        if (resourceKind.contains("linear")) {
            return "cuMemAlloc_v2,cuMemcpyHtoD_v2,cuTexObjectCreate,cuTexObjectDestroy,cuMemFree_v2";
        }
        return "cuArray3DCreate,cuMemcpy2D_v2,cuMemcpy3D_v2,cuTexObjectCreate,cuTexObjectDestroy";
    }

    private static String surfaceSymbols(String resourceKind) {
        if (resourceKind.contains("mipmapped")) {
            return "cuMipmappedArrayCreate,cuMipmappedArrayGetLevel,cuSurfObjectCreate,cuSurfObjectDestroy,cuMipmappedArrayDestroy";
        }
        if (resourceKind.contains("linear")) {
            return "cuMemAlloc_v2,cuMemcpyHtoD_v2,cuSurfObjectCreate,cuSurfObjectDestroy,cuMemFree_v2";
        }
        return "cuArray3DCreate,cuMemcpy2D_v2,cuMemcpy3D_v2,cuSurfObjectCreate,cuSurfObjectDestroy";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
