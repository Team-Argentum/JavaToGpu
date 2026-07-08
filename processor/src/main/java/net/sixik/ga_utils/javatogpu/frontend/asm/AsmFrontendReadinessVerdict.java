package net.sixik.ga_utils.javatogpu.frontend.asm;

/**
 * High-level outcome for CI/build tooling that preflights arbitrary ASM artifacts.
 */
public enum AsmFrontendReadinessVerdict {
    SUPPORTED("supported"),
    REWRITE_REQUIRED("rewriteRequired"),
    REJECTED("rejected");

    private final String artifactValue;

    AsmFrontendReadinessVerdict(String artifactValue) {
        this.artifactValue = artifactValue;
    }

    public String artifactValue() {
        return artifactValue;
    }
}
