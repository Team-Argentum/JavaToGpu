package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Objects;
import java.util.Optional;

/**
 * Renders no-candidate auto-vectorization hints in a compact Rust-like diagnostic shape.
 */
public final class GpuIrAutoVectorizationNoCandidateDiagnosticFormatter {
    public static final String DIAGNOSTIC_CODE = "JTG-IR-AV-001";

    public Optional<String> format(
            String methodName,
            GpuIrAutoVectorizationNoCandidateBucketSummaryReport report
    ) {
        Objects.requireNonNull(report, "report");
        return report.firstExample()
                .map(example -> format(methodName, report, example));
    }

    public String helpFor(GpuIrAutoVectorizationNoCandidateBucketSummaryReport report) {
        Objects.requireNonNull(report, "report");
        return helpForRemainingWork(report.firstRemainingWork());
    }

    private String format(
            String methodName,
            GpuIrAutoVectorizationNoCandidateBucketSummaryReport report,
            GpuIrAutoVectorizationNoCandidateExample example
    ) {
        String safeMethodName = methodName == null || methodName.isBlank() ? example.methodName() : methodName;
        return "note[" + DIAGNOSTIC_CODE + "]: auto-vectorization found no rewrite candidate\n"
                + "  --> " + safeMethodName + "@" + example.location() + "\n"
                + "   = bucket: " + example.bucket() + "\n"
                + "   = reason: " + example.summary() + "\n"
                + "   = help: " + helpFor(report);
    }

    private String helpForRemainingWork(String remainingWork) {
        return switch (remainingWork) {
            case "addVectorizableWorkOrSkipVectorization" -> "add lane-wise array work or keep this method scalar-only";
            case "skipOrDocumentScalarOnlyMethod" -> "keep the method scalar-only or introduce lane-wise array work before expecting vectorization";
            case "introduceOrDetectLaneLoop" -> "use a supported fixed-width lane loop such as int i = 0; i < 4; i = i + 1";
            case "supportWhileLikeLoopDiscovery" -> "rewrite the loop as a supported for-loop or extend scanner support for while-like loops";
            case "normalizeLoopToSupportedForShape" -> "normalize the loop to int i = 0; i < laneCount; i = i + 1";
            case "adjustLaneCountOrExtendSupportedLanes" -> "use a supported lane count 2, 3, 4, 8, or 16, or add backend proof for a new width";
            case "detectOrIntroduceLaneArrayAssignment" -> "write an array element indexed by the lane variable inside the fixed-width loop";
            case "fixIncompleteIrBeforeVectorization" -> "fix missing lowered IR nodes before running vectorization analysis";
            case "classifyScalarOrLoopShape" -> "inspect the first IR example and add a more specific scanner shape bucket if needed";
            default -> "review auto-vectorization candidate discovery for this IR shape";
        };
    }
}
