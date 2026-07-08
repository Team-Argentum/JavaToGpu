package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Parse result for one serialized ir-text-v1 method body.
 */
public record OpenClIrTextBodyParseResult(
        boolean parsed,
        List<OpenClIrTextStatement> statements,
        List<String> blockers,
        List<String> diagnostics
) {

    public OpenClIrTextBodyParseResult {
        statements = statements == null ? List.of() : List.copyOf(statements);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
