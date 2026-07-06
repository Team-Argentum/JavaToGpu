package net.sixik.ga_utils.javatogpu.frontend.asm;

/**
 * Suggested integration action for an unsupported ASM failure family.
 */
public enum AsmFrontendFailureAction {
    SUPPORTED("supported"),
    REWRITE_TO_GPU_SAFE_ASM("rewriteToGpuSafeAsm"),
    REJECT_UNTIL_MANUAL_REDESIGN("rejectUntilManualRedesign");

    private final String artifactValue;

    AsmFrontendFailureAction(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
