package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Result of lowering a parsed ir-text-v1 method body into OpenCL-C statements.
 */
public record OpenClIrTextBodyEmissionResult(
        boolean emitted,
        String body,
        List<String> blockers,
        List<String> diagnostics
) {

    public OpenClIrTextBodyEmissionResult {
        body = body == null ? "" : body;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
