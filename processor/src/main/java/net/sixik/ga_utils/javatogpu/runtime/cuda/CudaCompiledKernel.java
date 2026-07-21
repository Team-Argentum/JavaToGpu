package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CUDA compile-stage artifact used before native argument binding and kernel launch exist.
 */
public final class CudaCompiledKernel implements GpuBackendCompiledKernel {

    private final GpuKernelDescriptor descriptor;
    private final String cacheKey;
    private final GpuRuntimeCompileArtifactSnapshot artifactSnapshot;
    private final String compilerBridgeMode;
    private final boolean nativeHandleAvailable;
    private final String sourceSha256;
    private final CudaNativeCompilationResult nativeCompilationResult;
    private final GpuBackendCompileOptions backendOptions;
    private final GpuRuntimeDeviceProfile deviceProfile;

    private CudaCompiledKernel(
            GpuKernelDescriptor descriptor,
            String cacheKey,
            GpuRuntimeCompileArtifactSnapshot artifactSnapshot,
            String compilerBridgeMode,
            boolean nativeHandleAvailable,
            String sourceSha256,
            CudaNativeCompilationResult nativeCompilationResult,
            GpuBackendCompileOptions backendOptions,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.cacheKey = normalize(cacheKey, "cuda-preview:" + sourceSha256);
        this.artifactSnapshot = Objects.requireNonNull(artifactSnapshot, "artifactSnapshot");
        this.compilerBridgeMode = normalize(compilerBridgeMode, "source-preview");
        this.nativeHandleAvailable = nativeHandleAvailable;
        this.sourceSha256 = normalize(sourceSha256, "unknown");
        this.nativeCompilationResult = nativeCompilationResult;
        this.backendOptions = backendOptions == null
                ? GpuBackendCompileOptions.empty(GpuBackendTarget.CUDA)
                : backendOptions;
        this.deviceProfile = deviceProfile == null
                ? GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
                : deviceProfile;
    }

    static CudaCompiledKernel preview(GpuRuntimeCompileRequest compileRequest, GpuBackendModuleArtifact moduleArtifact) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        GpuBackendModuleArtifact module = moduleArtifact == null ? GpuBackendModuleArtifact.unknown() : moduleArtifact;
        String sourceSha256 = sha256(module.source());
        String cacheKey = "cuda-preview:"
                + module.moduleFormat().key()
                + ":"
                + compileRequest.descriptor().kernelName()
                + ":"
                + sourceSha256;
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot
                .from(compileRequest, compileRequest, module)
                .withCompileLog("CUDA compile preview accepted " + module.moduleFormat().key()
                        + " module; native CUDA compiler bridge is not wired yet")
                .withRuntimeValidationEvidence(List.of(
                        "cuda.compilePreview.accepted=true",
                        "cuda.nativeHandle.available=false"
                ));
        return new CudaCompiledKernel(
                compileRequest.descriptor(),
                cacheKey,
                snapshot,
                module.moduleFormat() == GpuBackendModuleFormat.PTX ? "ptx-preview" : "source-preview",
                false,
                sourceSha256,
                null,
                compileRequest.options().backendOptions(),
                compileRequest.deviceProfile()
        );
    }

    static CudaCompiledKernel nativeCompiled(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact sourceModule,
            CudaNativeCompilationResult nativeCompilationResult
    ) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        Objects.requireNonNull(nativeCompilationResult, "nativeCompilationResult");
        GpuBackendModuleArtifact compiledModule = nativeCompilationResult.moduleArtifact().orElse(sourceModule);
        String sourceSha256 = sha256(sourceModule == null ? "" : sourceModule.source());
        String cacheKey = "cuda-native:"
                + nativeCompilationResult.bridgeId()
                + ":"
                + compiledModule.moduleFormat().key()
                + ":"
                + compileRequest.descriptor().kernelName()
                + ":"
                + modulePayloadIdentity(compiledModule);
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot
                .from(compileRequest, compileRequest, compiledModule)
                .withBinaryArtifacts(nativeCompilationResult.binaryArtifacts())
                .withCompileLog(nativeCompilationResult.compileLog())
                .withRuntimeValidationEvidence(List.of(
                        "cuda.nativeCompilation.bridge=" + nativeCompilationResult.bridgeId(),
                        "cuda.nativeCompilation.status=" + nativeCompilationResult.status(),
                        "cuda.nativeCompilation.binaryArtifact.count=" + nativeCompilationResult.binaryArtifacts().size(),
                        "cuda.nativeHandle.available=false"
                ));
        return new CudaCompiledKernel(
                compileRequest.descriptor(),
                cacheKey,
                snapshot,
                nativeCompilationResult.bridgeId(),
                false,
                sourceSha256,
                nativeCompilationResult,
                compileRequest.options().backendOptions(),
                compileRequest.deviceProfile()
        );
    }

    @Override
    public GpuKernelDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String cacheKey() {
        return cacheKey;
    }

    @Override
    public GpuRuntimeCompileArtifactSnapshot artifactSnapshot() {
        return artifactSnapshot;
    }

    @Override
    public String compiledKernelKind() {
        return "cuda-compile-preview";
    }

    public String compilerBridgeMode() {
        return compilerBridgeMode;
    }

    public boolean nativeHandleAvailable() {
        return nativeHandleAvailable;
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    public GpuBackendCompileOptions backendOptions() {
        return backendOptions;
    }

    GpuRuntimeDeviceProfile deviceProfile() {
        return deviceProfile;
    }

    @Override
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.compiledKernel" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>(GpuBackendCompiledKernel.super.artifactFields(normalizedPrefix));
        fields.put(normalizedPrefix + ".compilerBridge.mode", compilerBridgeMode);
        fields.put(normalizedPrefix + ".nativeHandle.available", Boolean.toString(nativeHandleAvailable));
        fields.put(normalizedPrefix + ".source.sha256", sourceSha256);
        if (nativeCompilationResult != null) {
            fields.putAll(nativeCompilationResult.artifactFields(normalizedPrefix + ".nativeCompilation"));
        }
        fields.put("runtime.backend.compiledKernel.compilerBridge.mode", compilerBridgeMode);
        fields.put("runtime.backend.compiledKernel.nativeHandle.available", Boolean.toString(nativeHandleAvailable));
        fields.put("runtime.cuda.compiledKernel.present", "true");
        fields.put("runtime.cuda.compiledKernel.compilerBridge.mode", compilerBridgeMode);
        fields.put("runtime.cuda.compiledKernel.nativeHandle.available", Boolean.toString(nativeHandleAvailable));
        fields.put("runtime.cuda.compiledKernel.source.sha256", sourceSha256);
        return Collections.unmodifiableMap(fields);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private static String modulePayloadIdentity(GpuBackendModuleArtifact moduleArtifact) {
        GpuBackendModuleArtifact module = moduleArtifact == null ? GpuBackendModuleArtifact.unknown() : moduleArtifact;
        if (module.sourceAvailable()) {
            return sha256(module.source());
        }
        return sha256(module.format()
                + "|"
                + module.resource()
                + "|"
                + module.artifactVersion()
                + "|"
                + module.binaryAvailable());
    }

    private static String normalize(String value, String fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
