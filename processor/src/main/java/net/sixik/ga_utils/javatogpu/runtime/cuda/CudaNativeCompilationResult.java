package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result from an optional CUDA-native compiler bridge.
 */
public record CudaNativeCompilationResult(
        String bridgeId,
        String status,
        Optional<GpuBackendModuleArtifact> moduleArtifact,
        String compileLog,
        List<String> blockers,
        List<String> diagnostics
) {

    public CudaNativeCompilationResult {
        bridgeId = bridgeId == null || bridgeId.isBlank() ? "cuda-native-compiler:unknown" : bridgeId.trim();
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        moduleArtifact = moduleArtifact == null ? Optional.empty() : moduleArtifact;
        compileLog = compileLog == null ? "" : compileLog;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static CudaNativeCompilationResult disabled(String bridgeMode) {
        return new CudaNativeCompilationResult(
                bridgeId(bridgeMode),
                "disabled",
                Optional.empty(),
                "",
                List.of("cuda-native-compiler-bridge-disabled"),
                List.of("CUDA native compiler bridge was not requested")
        );
    }

    public static CudaNativeCompilationResult unsupported(
            String bridgeMode,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaNativeCompilationResult(
                bridgeId(bridgeMode),
                "unsupported",
                Optional.empty(),
                "",
                blockers,
                diagnostics
        );
    }

    public static CudaNativeCompilationResult failed(
            String bridgeId,
            String compileLog,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaNativeCompilationResult(
                bridgeId,
                "failed",
                Optional.empty(),
                compileLog,
                blockers,
                diagnostics
        );
    }

    public static CudaNativeCompilationResult succeeded(
            String bridgeId,
            GpuBackendModuleArtifact moduleArtifact,
            String compileLog,
            List<String> diagnostics
    ) {
        return new CudaNativeCompilationResult(
                bridgeId,
                "succeeded",
                Optional.ofNullable(moduleArtifact),
                compileLog,
                List.of(),
                diagnostics
        );
    }

    public boolean succeeded() {
        return "succeeded".equals(status) && moduleArtifact.isPresent();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.nativeCompilation"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".bridge.id", bridgeId);
        fields.put(normalizedPrefix + ".status", status);
        fields.put(normalizedPrefix + ".succeeded", Boolean.toString(succeeded()));
        fields.put(normalizedPrefix + ".module.present", Boolean.toString(moduleArtifact.isPresent()));
        moduleArtifact.ifPresent(module -> {
            fields.put(normalizedPrefix + ".module.format", module.format());
            fields.put(normalizedPrefix + ".module.resource", module.resource());
        });
        fields.put(normalizedPrefix + ".compileLog.present", Boolean.toString(!compileLog.isBlank()));
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put("runtime.cuda.nativeCompilation.present", "true");
        fields.put("runtime.cuda.nativeCompilation.bridge.id", bridgeId);
        fields.put("runtime.cuda.nativeCompilation.status", status);
        fields.put("runtime.cuda.nativeCompilation.succeeded", Boolean.toString(succeeded()));
        return Collections.unmodifiableMap(fields);
    }

    private static String bridgeId(String bridgeMode) {
        return bridgeMode == null || bridgeMode.isBlank()
                ? "cuda-native-compiler:unknown"
                : "cuda-native-compiler:" + bridgeMode.trim();
    }
}
