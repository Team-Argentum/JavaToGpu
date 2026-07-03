package net.sixik.ga_utils.javatogpu.irvalidation;

/**
 * Read-only classifier for repeated-expression candidates reported by the scanner.
 */
public final class GpuIrCommonSubexpressionClassifier {
    public GpuIrCommonSubexpressionKind classify(GpuIrCommonSubexpression candidate) {
        String fingerprint = candidate.fingerprint();
        if (fingerprint.startsWith("helper(")) {
            return GpuIrCommonSubexpressionKind.HELPER_REUSE;
        }
        if (fingerprint.startsWith("intrinsic(")) {
            return GpuIrCommonSubexpressionKind.INTRINSIC_REUSE;
        }
        if (isLeafFingerprint(fingerprint) || isUnknownFingerprint(fingerprint)) {
            return GpuIrCommonSubexpressionKind.UNSAFE_FOR_REWRITE;
        }
        return GpuIrCommonSubexpressionKind.LOCAL_REUSE;
    }

    public boolean isRewriteReady(GpuIrCommonSubexpression candidate) {
        return classify(candidate) == GpuIrCommonSubexpressionKind.LOCAL_REUSE;
    }

    private boolean isLeafFingerprint(String fingerprint) {
        return fingerprint.startsWith("var(") || fingerprint.startsWith("literal(");
    }

    private boolean isUnknownFingerprint(String fingerprint) {
        return !(fingerprint.startsWith("array(")
                || fingerprint.startsWith("field(")
                || fingerprint.startsWith("binary(")
                || fingerprint.startsWith("binary_assoc(")
                || fingerprint.startsWith("unary(")
                || fingerprint.startsWith("ternary(")
                || fingerprint.startsWith("cast(")
                || fingerprint.startsWith("struct(")
                || fingerprint.startsWith("helper(")
                || fingerprint.startsWith("intrinsic("));
    }
}
