package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Result of assembling emitted IrGpu method bodies into a complete OpenCL-C source unit.
 */
public record OpenClIrGpuSourceAssemblyResult(
        boolean assembled,
        String source,
        List<String> blockers,
        List<String> diagnostics
) {

    public OpenClIrGpuSourceAssemblyResult {
        source = source == null ? "" : source;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
