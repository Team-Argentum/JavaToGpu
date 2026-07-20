package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of loading a CUDA module/function handle from a compiled CUDA artifact.
 */
public record CudaModuleLoadResult(
        String loaderId,
        String status,
        String moduleHandleKind,
        String functionHandleKind,
        CudaDriverLoadedModule loadedModule,
        List<String> blockers,
        List<String> diagnostics
) {

    public CudaModuleLoadResult {
        loaderId = loaderId == null || loaderId.isBlank() ? "cuda-module-loader:unknown" : loaderId.trim();
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        moduleHandleKind = moduleHandleKind == null || moduleHandleKind.isBlank() ? "none" : moduleHandleKind.trim();
        functionHandleKind = functionHandleKind == null || functionHandleKind.isBlank() ? "none" : functionHandleKind.trim();
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static CudaModuleLoadResult disabled(String loaderMode) {
        return new CudaModuleLoadResult(
                loaderId(loaderMode),
                "disabled",
                "none",
                "none",
                null,
                List.of("cuda-native-module-loader-disabled"),
                List.of("CUDA native module loader was not requested")
        );
    }

    public static CudaModuleLoadResult unsupported(
            String loaderMode,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaModuleLoadResult(loaderId(loaderMode), "unsupported", "none", "none", null, blockers, diagnostics);
    }

    public static CudaModuleLoadResult failed(
            String loaderId,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new CudaModuleLoadResult(loaderId, "failed", "none", "none", null, blockers, diagnostics);
    }

    public static CudaModuleLoadResult succeeded(
            String loaderId,
            String moduleHandleKind,
            String functionHandleKind,
            List<String> diagnostics
    ) {
        return new CudaModuleLoadResult(
                loaderId,
                "succeeded",
                moduleHandleKind,
                functionHandleKind,
                null,
                List.of(),
                diagnostics
        );
    }

    public static CudaModuleLoadResult succeeded(
            String loaderId,
            CudaDriverLoadedModule loadedModule,
            List<String> diagnostics
    ) {
        return new CudaModuleLoadResult(
                loaderId,
                "succeeded",
                "cuda-driver-module-handle",
                "cuda-driver-function-handle",
                loadedModule,
                List.of(),
                diagnostics
        );
    }

    public boolean succeeded() {
        return "succeeded".equals(status);
    }

    public Optional<String> firstBlocker() {
        return blockers.stream().findFirst();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.cuda.moduleLoad" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".loader.id", loaderId);
        fields.put(normalizedPrefix + ".status", status);
        fields.put(normalizedPrefix + ".succeeded", Boolean.toString(succeeded()));
        fields.put(normalizedPrefix + ".moduleHandle.kind", moduleHandleKind);
        fields.put(normalizedPrefix + ".functionHandle.kind", functionHandleKind);
        fields.put(normalizedPrefix + ".nativeHandle.present", Boolean.toString(loadedModule != null));
        if (loadedModule != null) {
            fields.putAll(loadedModule.artifactFields(normalizedPrefix + ".driverLoadedModule"));
        }
        fields.put(normalizedPrefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(normalizedPrefix + ".blocker." + index, blockers.get(index));
        }
        fields.put("runtime.cuda.moduleLoad.present", "true");
        fields.put("runtime.cuda.moduleLoad.loader.id", loaderId);
        fields.put("runtime.cuda.moduleLoad.status", status);
        fields.put("runtime.cuda.moduleLoad.succeeded", Boolean.toString(succeeded()));
        fields.put("runtime.cuda.moduleLoad.nativeHandle.present", Boolean.toString(loadedModule != null));
        return Collections.unmodifiableMap(fields);
    }

    private static String loaderId(String loaderMode) {
        return loaderMode == null || loaderMode.isBlank()
                ? "cuda-module-loader:unknown"
                : "cuda-module-loader:" + loaderMode.trim();
    }
}
