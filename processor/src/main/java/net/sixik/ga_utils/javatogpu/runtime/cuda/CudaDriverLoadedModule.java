package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns a CUDA Driver API module/function pair and unloads the module on close.
 */
public final class CudaDriverLoadedModule implements AutoCloseable {

    private final String loaderId;
    private final String libraryName;
    private final String libraryPath;
    private final String kernelName;
    private final long moduleHandle;
    private final long functionHandle;
    private final long moduleUnloadAddress;
    private final int driverVersionRaw;
    private final String driverVersion;
    private final CudaPtxCompatibilityMetadata ptxMetadata;
    private final CudaPtxDeviceCompatibility ptxDeviceCompatibility;
    private final CudaPtxDriverCompatibility ptxDriverCompatibility;
    private final String moduleFormat;
    private final int modulePayloadByteSize;
    private final long contextHandle;
    private final int contextDevice;
    private final long contextSetCurrentAddress;
    private final long primaryContextReleaseAddress;
    private final CudaDriverLibrary.SharedLibraryHandle libraryHandle;
    private final CudaDriverLibrary.DriverApiInvoker invoker;
    private final List<String> diagnostics;
    private boolean closed;
    private int closeStatus;
    private int contextReleaseStatus;

    CudaDriverLoadedModule(
            String loaderId,
            String libraryName,
            String libraryPath,
            String kernelName,
            long moduleHandle,
            long functionHandle,
            long moduleUnloadAddress,
            CudaDriverLibrary.SharedLibraryHandle libraryHandle,
            CudaDriverLibrary.DriverApiInvoker invoker,
            List<String> diagnostics
    ) {
        this(
                loaderId,
                libraryName,
                libraryPath,
                kernelName,
                moduleHandle,
                functionHandle,
                moduleUnloadAddress,
                0,
                "unknown",
                CudaPtxCompatibilityMetadata.fromSource(""),
                CudaPtxDeviceCompatibility.evaluate(CudaPtxCompatibilityMetadata.fromSource(""), null),
                CudaPtxDriverCompatibility.evaluate(CudaPtxCompatibilityMetadata.fromSource(""), 0),
                "unknown",
                0,
                0L,
                -1,
                0L,
                0L,
                libraryHandle,
                invoker,
                diagnostics
        );
    }

    CudaDriverLoadedModule(
            String loaderId,
            String libraryName,
            String libraryPath,
            String kernelName,
            long moduleHandle,
            long functionHandle,
            long moduleUnloadAddress,
            int driverVersionRaw,
            String driverVersion,
            CudaPtxCompatibilityMetadata ptxMetadata,
            CudaPtxDeviceCompatibility ptxDeviceCompatibility,
            CudaPtxDriverCompatibility ptxDriverCompatibility,
            String moduleFormat,
            int modulePayloadByteSize,
            long contextHandle,
            int contextDevice,
            long contextSetCurrentAddress,
            long primaryContextReleaseAddress,
            CudaDriverLibrary.SharedLibraryHandle libraryHandle,
            CudaDriverLibrary.DriverApiInvoker invoker,
            List<String> diagnostics
    ) {
        this.loaderId = normalize(loaderId, "cuda-module-loader:driver");
        this.libraryName = libraryName == null ? "" : libraryName.trim();
        this.libraryPath = libraryPath == null ? "" : libraryPath.trim();
        this.kernelName = normalize(kernelName, "unknown");
        this.moduleHandle = moduleHandle;
        this.functionHandle = functionHandle;
        this.moduleUnloadAddress = moduleUnloadAddress;
        this.driverVersionRaw = Math.max(0, driverVersionRaw);
        this.driverVersion = normalize(driverVersion, CudaDriverLibrary.formatDriverVersion(this.driverVersionRaw));
        this.ptxMetadata = ptxMetadata == null ? CudaPtxCompatibilityMetadata.fromSource("") : ptxMetadata;
        this.ptxDeviceCompatibility = ptxDeviceCompatibility == null
                ? CudaPtxDeviceCompatibility.evaluate(this.ptxMetadata, null)
                : ptxDeviceCompatibility;
        this.ptxDriverCompatibility = ptxDriverCompatibility == null
                ? CudaPtxDriverCompatibility.evaluate(this.ptxMetadata, this.driverVersionRaw)
                : ptxDriverCompatibility;
        this.moduleFormat = normalize(moduleFormat, "unknown");
        this.modulePayloadByteSize = Math.max(0, modulePayloadByteSize);
        this.contextHandle = contextHandle;
        this.contextDevice = contextDevice;
        this.contextSetCurrentAddress = contextSetCurrentAddress;
        this.primaryContextReleaseAddress = primaryContextReleaseAddress;
        this.libraryHandle = Objects.requireNonNull(libraryHandle, "libraryHandle");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public String loaderId() {
        return loaderId;
    }

    public String libraryName() {
        return libraryName;
    }

    public String libraryPath() {
        return libraryPath;
    }

    public String kernelName() {
        return kernelName;
    }

    public long moduleHandle() {
        return moduleHandle;
    }

    public long functionHandle() {
        return functionHandle;
    }

    public int driverVersionRaw() {
        return driverVersionRaw;
    }

    public String driverVersion() {
        return driverVersion;
    }

    CudaPtxCompatibilityMetadata ptxMetadata() {
        return ptxMetadata;
    }

    CudaPtxDeviceCompatibility ptxDeviceCompatibility() {
        return ptxDeviceCompatibility;
    }

    CudaPtxDriverCompatibility ptxDriverCompatibility() {
        return ptxDriverCompatibility;
    }

    public String moduleFormat() {
        return moduleFormat;
    }

    public int modulePayloadByteSize() {
        return modulePayloadByteSize;
    }

    long findSymbol(String symbolName) {
        return libraryHandle.findSymbol(symbolName);
    }

    CudaDriverLibrary.DriverApiInvoker driverApiInvoker() {
        return invoker;
    }

    public boolean closed() {
        return closed;
    }

    public int closeStatus() {
        return closeStatus;
    }

    public int contextReleaseStatus() {
        return contextReleaseStatus;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.loadedModule"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".loader.id", loaderId);
        fields.put(normalizedPrefix + ".library.name", libraryName);
        fields.put(normalizedPrefix + ".library.path", libraryPath);
        fields.put(normalizedPrefix + ".kernel.name", kernelName);
        fields.put(normalizedPrefix + ".module.format", moduleFormat);
        fields.put(normalizedPrefix + ".module.payload.byteSize", Integer.toString(modulePayloadByteSize));
        fields.put(normalizedPrefix + ".driver.version.raw", Integer.toString(driverVersionRaw));
        fields.put(normalizedPrefix + ".driver.version", driverVersion);
        fields.putAll(ptxMetadata.artifactFields(normalizedPrefix + ".ptxCompatibility"));
        fields.putAll(ptxDeviceCompatibility.artifactFields(normalizedPrefix + ".ptxDeviceCompatibility"));
        fields.putAll(ptxDriverCompatibility.artifactFields(normalizedPrefix + ".ptxDriverCompatibility"));
        fields.put(normalizedPrefix + ".moduleHandle.present", Boolean.toString(moduleHandle != 0L));
        fields.put(normalizedPrefix + ".functionHandle.present", Boolean.toString(functionHandle != 0L));
        fields.put(normalizedPrefix + ".contextHandle.present", Boolean.toString(contextHandle != 0L));
        fields.put(normalizedPrefix + ".closed", Boolean.toString(closed));
        fields.put(normalizedPrefix + ".close.status", Integer.toString(closeStatus));
        fields.put(normalizedPrefix + ".contextRelease.status", Integer.toString(contextReleaseStatus));
        fields.put("runtime.cuda.loadedModule.present", "true");
        fields.put("runtime.cuda.loadedModule.loader.id", loaderId);
        fields.put("runtime.cuda.loadedModule.module.format", moduleFormat);
        fields.put("runtime.cuda.loadedModule.module.payload.byteSize", Integer.toString(modulePayloadByteSize));
        fields.put("runtime.cuda.loadedModule.driver.version.raw", Integer.toString(driverVersionRaw));
        fields.put("runtime.cuda.loadedModule.driver.version", driverVersion);
        fields.put("runtime.cuda.loadedModule.moduleHandle.present", Boolean.toString(moduleHandle != 0L));
        fields.put("runtime.cuda.loadedModule.functionHandle.present", Boolean.toString(functionHandle != 0L));
        fields.put("runtime.cuda.loadedModule.contextHandle.present", Boolean.toString(contextHandle != 0L));
        fields.put("runtime.cuda.loadedModule.closed", Boolean.toString(closed));
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        RuntimeException failure = null;
        if (contextHandle != 0L && contextSetCurrentAddress != 0L) {
            int setCurrentStatus = invoker.cuCtxSetCurrent(contextHandle, contextSetCurrentAddress);
            if (setCurrentStatus != CudaDriverLibrary.CUDA_SUCCESS) {
                failure = new IllegalStateException("CUDA cuCtxSetCurrent during close failed with code " + setCurrentStatus);
            }
        }
        if (moduleHandle != 0L) {
            closeStatus = invoker.cuModuleUnload(moduleHandle, moduleUnloadAddress);
            if (closeStatus != CudaDriverLibrary.CUDA_SUCCESS) {
                failure = appendFailure(failure, new IllegalStateException("CUDA cuModuleUnload failed with code " + closeStatus));
            }
        }
        if (contextDevice >= 0 && primaryContextReleaseAddress != 0L) {
            contextReleaseStatus = invoker.cuDevicePrimaryCtxRelease(contextDevice, primaryContextReleaseAddress);
            if (contextReleaseStatus != CudaDriverLibrary.CUDA_SUCCESS) {
                failure = appendFailure(failure, new IllegalStateException(
                        "CUDA cuDevicePrimaryCtxRelease failed with code " + contextReleaseStatus));
            }
        }
        try {
            libraryHandle.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        } finally {
            closed = true;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException appendFailure(RuntimeException existing, RuntimeException next) {
        if (existing == null) {
            return next;
        }
        existing.addSuppressed(next);
        return existing;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
