package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Parsed switch case from the transitional ir-text-v1 body format.
 *
 * <p>Case labels are kept as opaque expression text so reconstruction can support Java switch shapes without taking
 * ownership of typed expression lowering yet.
 */
public record OpenClIrTextSwitchCase(
        int lineNumber,
        boolean defaultCase,
        List<String> labels,
        List<OpenClIrTextStatement> statements
) {

    public OpenClIrTextSwitchCase {
        labels = labels == null ? List.of() : List.copyOf(labels);
        statements = statements == null ? List.of() : List.copyOf(statements);
    }

    public static OpenClIrTextSwitchCase caseBlock(
            int lineNumber,
            List<String> labels,
            List<OpenClIrTextStatement> statements
    ) {
        return new OpenClIrTextSwitchCase(lineNumber, false, labels, statements);
    }

    public static OpenClIrTextSwitchCase defaultBlock(int lineNumber, List<OpenClIrTextStatement> statements) {
        return new OpenClIrTextSwitchCase(lineNumber, true, List.of(), statements);
    }
}
