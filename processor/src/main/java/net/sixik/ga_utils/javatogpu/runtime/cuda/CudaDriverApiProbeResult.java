package net.sixik.ga_utils.javatogpu.runtime.cuda;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of probing the CUDA Driver API shared library without creating runtime handles.
 */
public record CudaDriverApiProbeResult(
        String status,
        String libraryName,
        String libraryPath,
        List<String> resolvedSymbols,
        List<String> missingSymbols,
        List<String> blockers,
        List<String> diagnostics
) {

    public CudaDriverApiProbeResult {
        status = status == null || status.isBlank() ? "unknown" : status.trim();
        libraryName = libraryName == null ? "" : libraryName.trim();
        libraryPath = libraryPath == null ? "" : libraryPath.trim();
        resolvedSymbols = resolvedSymbols == null ? List.of() : List.copyOf(resolvedSymbols);
        missingSymbols = missingSymbols == null ? List.of() : List.copyOf(missingSymbols);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    static CudaDriverApiProbeResult available(
            String libraryName,
            String libraryPath,
            List<String> resolvedSymbols,
            List<String> diagnostics
    ) {
        return new CudaDriverApiProbeResult(
                "available",
                libraryName,
                libraryPath,
                resolvedSymbols,
                List.of(),
                List.of(),
                diagnostics
        );
    }

    static CudaDriverApiProbeResult unavailable(List<String> diagnostics) {
        return new CudaDriverApiProbeResult(
                "unavailable",
                "",
                "",
                List.of(),
                List.of(),
                List.of("cuda-driver-library-unavailable"),
                diagnostics
        );
    }

    static CudaDriverApiProbeResult missingSymbols(
            String libraryName,
            String libraryPath,
            List<String> resolvedSymbols,
            List<String> missingSymbols,
            List<String> diagnostics
    ) {
        return new CudaDriverApiProbeResult(
                "missing-symbols",
                libraryName,
                libraryPath,
                resolvedSymbols,
                missingSymbols,
                missingSymbolBlockers(missingSymbols),
                diagnostics
        );
    }

    public boolean available() {
        return "available".equals(status) && blockers.isEmpty() && missingSymbols.isEmpty();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.cuda.driverApi" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.driverApi");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".status", status);
        fields.put(prefix + ".available", Boolean.toString(available()));
        fields.put(prefix + ".library.name", libraryName);
        fields.put(prefix + ".library.path", libraryPath);
        fields.put(prefix + ".symbol.resolved.count", Integer.toString(resolvedSymbols.size()));
        fields.put(prefix + ".symbol.missing.count", Integer.toString(missingSymbols.size()));
        fields.put(prefix + ".blocker.count", Integer.toString(blockers.size()));
        for (int index = 0; index < blockers.size(); index++) {
            fields.put(prefix + ".blocker." + index, blockers.get(index));
        }
    }

    private static List<String> missingSymbolBlockers(List<String> missingSymbols) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("cuda-driver-symbols-missing"),
                        missingSymbols.stream().map(symbol -> "cuda-driver-symbol-missing:" + symbol)
                )
                .toList();
    }
}
