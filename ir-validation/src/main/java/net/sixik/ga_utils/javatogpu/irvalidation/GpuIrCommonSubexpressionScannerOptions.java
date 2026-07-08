package net.sixik.ga_utils.javatogpu.irvalidation;

public record GpuIrCommonSubexpressionScannerOptions(
        int minOccurrences,
        boolean includeTrivialLeafExpressions
) {
    public GpuIrCommonSubexpressionScannerOptions {
        if (minOccurrences < 2) {
            throw new IllegalArgumentException("minOccurrences must be at least 2");
        }
    }

    public static GpuIrCommonSubexpressionScannerOptions diagnosticDefaults() {
        // Diagnostic scans keep leaf expressions so reports explain every repeated IR fragment.
        return new GpuIrCommonSubexpressionScannerOptions(2, true);
    }

    public static GpuIrCommonSubexpressionScannerOptions optimizerFocused() {
        // Optimizer scans skip vars/literals because those are rarely useful rewrite candidates.
        return new GpuIrCommonSubexpressionScannerOptions(2, false);
    }
}
