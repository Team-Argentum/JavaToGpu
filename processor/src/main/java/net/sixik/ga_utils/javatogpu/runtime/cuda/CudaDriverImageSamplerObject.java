package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Owns one CUDA texture/surface object handle once image/sampler runtime binding exists.
 */
final class CudaDriverImageSamplerObject implements AutoCloseable {

    private final int parameterIndex;
    private final String parameterName;
    private final String javaType;
    private final String objectKind;
    private final String cudaResourceKind;
    private final String parameterCarrier;
    private final long objectHandle;
    private final long destroyFunctionAddress;
    private final CudaDriverLibrary.DriverApiInvoker invoker;
    private boolean closed;
    private int closeStatus = -1;

    private CudaDriverImageSamplerObject(
            int parameterIndex,
            String parameterName,
            String javaType,
            String objectKind,
            String cudaResourceKind,
            String parameterCarrier,
            long objectHandle,
            long destroyFunctionAddress,
            CudaDriverLibrary.DriverApiInvoker invoker
    ) {
        this.parameterIndex = Math.max(0, parameterIndex);
        this.parameterName = parameterName == null || parameterName.isBlank()
                ? "arg" + this.parameterIndex
                : parameterName.trim();
        this.javaType = javaType == null || javaType.isBlank() ? "unknown" : javaType.trim();
        this.objectKind = normalizeKind(objectKind);
        this.cudaResourceKind = cudaResourceKind == null || cudaResourceKind.isBlank()
                ? "unknown"
                : cudaResourceKind.trim();
        this.parameterCarrier = parameterCarrier == null || parameterCarrier.isBlank()
                ? carrierFor(this.objectKind)
                : parameterCarrier.trim();
        this.objectHandle = Math.max(0L, objectHandle);
        this.destroyFunctionAddress = Math.max(0L, destroyFunctionAddress);
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    static CudaDriverImageSamplerObject texture(
            int parameterIndex,
            String parameterName,
            String javaType,
            String cudaResourceKind,
            long textureObjectHandle,
            long destroyFunctionAddress,
            CudaDriverLibrary.DriverApiInvoker invoker
    ) {
        return new CudaDriverImageSamplerObject(
                parameterIndex,
                parameterName,
                javaType,
                "texture",
                cudaResourceKind,
                "CUtexObject",
                textureObjectHandle,
                destroyFunctionAddress,
                invoker
        );
    }

    static CudaDriverImageSamplerObject surface(
            int parameterIndex,
            String parameterName,
            String javaType,
            String cudaResourceKind,
            long surfaceObjectHandle,
            long destroyFunctionAddress,
            CudaDriverLibrary.DriverApiInvoker invoker
    ) {
        return new CudaDriverImageSamplerObject(
                parameterIndex,
                parameterName,
                javaType,
                "surface",
                cudaResourceKind,
                "CUsurfObject",
                surfaceObjectHandle,
                destroyFunctionAddress,
                invoker
        );
    }

    int parameterIndex() {
        return parameterIndex;
    }

    String parameterName() {
        return parameterName;
    }

    String javaType() {
        return javaType;
    }

    String objectKind() {
        return objectKind;
    }

    String cudaResourceKind() {
        return cudaResourceKind;
    }

    String parameterCarrier() {
        return parameterCarrier;
    }

    long objectHandle() {
        return objectHandle;
    }

    boolean objectHandlePresent() {
        return objectHandle != 0L;
    }

    boolean destroyFunctionPresent() {
        return destroyFunctionAddress != 0L;
    }

    boolean closed() {
        return closed;
    }

    int closeStatus() {
        return closeStatus;
    }

    Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.imageSamplerObject"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.imageSamplerObject." + parameterIndex);
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".parameter.index", Integer.toString(parameterIndex));
        fields.put(prefix + ".parameter.name", parameterName);
        fields.put(prefix + ".parameter.javaType", javaType);
        fields.put(prefix + ".object.kind", objectKind);
        fields.put(prefix + ".cudaResource.kind", cudaResourceKind);
        fields.put(prefix + ".parameter.carrier", parameterCarrier);
        fields.put(prefix + ".handle.present", Boolean.toString(objectHandlePresent()));
        fields.put(prefix + ".destroyFunction.present", Boolean.toString(destroyFunctionPresent()));
        fields.put(prefix + ".closed", Boolean.toString(closed));
        fields.put(prefix + ".close.status", Integer.toString(closeStatus));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (objectHandle == 0L) {
            closeStatus = CudaDriverLibrary.CUDA_SUCCESS;
            closed = true;
            return;
        }
        closeStatus = switch (objectKind) {
            case "texture" -> invoker.cuTexObjectDestroy(objectHandle, destroyFunctionAddress);
            case "surface" -> invoker.cuSurfObjectDestroy(objectHandle, destroyFunctionAddress);
            default -> CudaDriverLibrary.DriverApiInvoker.CUDA_ERROR_NOT_SUPPORTED;
        };
        closed = true;
        if (closeStatus != CudaDriverLibrary.CUDA_SUCCESS) {
            throw new IllegalStateException("CUDA " + objectKind + " object destroy failed with code " + closeStatus);
        }
    }

    private static String normalizeKind(String objectKind) {
        if ("surface".equals(objectKind)) {
            return "surface";
        }
        return "texture";
    }

    private static String carrierFor(String objectKind) {
        return "surface".equals(objectKind) ? "CUsurfObject" : "CUtexObject";
    }
}
