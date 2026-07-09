package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fail-closed promotion gate for runtime optimizer profiles that could affect production execution.
 *
 * <p>The gate is diagnostic-only for now: it records why an optimizer profile is or is not promotion-ready, but it does
 * not select optimized IR by itself. Future A1/A2 evidence can relax individual checks without changing the runtime
 * artifact shape.</p>
 */
public record GpuRuntimeProductionOptimizerGate(
        String status,
        String optimizationProfile,
        boolean productionProfileRequested,
        boolean runtimeEquivalenceRequired,
        boolean runtimeEquivalencePassed,
        boolean fallbackClean,
        boolean strategyEvidenceBacked,
        boolean vendorPromotionEligible,
        boolean rollbackClean,
        List<String> diagnostics
) {

    public GpuRuntimeProductionOptimizerGate {
        status = normalize(status, "not-requested");
        optimizationProfile = normalize(optimizationProfile, "off");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeProductionOptimizerGate evaluate(
            GpuRuntimeCompileRequest request,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        GpuRuntimeCompileOptions options = request == null ? GpuRuntimeCompileOptions.defaults(null) : request.options();
        return evaluate(
                options.optimizationProfile(),
                request == null ? java.util.Optional.empty() : request.irGpuArtifact(),
                optimizationReport,
                runtimeEquivalenceEvidence == null
                        ? GpuRuntimeEquivalenceEvidence.notRun(request, "runtime equivalence was not executed")
                        : runtimeEquivalenceEvidence,
                fallbackEvidence
        );
    }

    public static GpuRuntimeProductionOptimizerGate evaluate(
            String optimizationProfile,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        return evaluate(
                optimizationProfile,
                java.util.Optional.empty(),
                optimizationReport,
                runtimeEquivalenceEvidence == null
                        ? GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed")
                        : runtimeEquivalenceEvidence,
                fallbackEvidence
        );
    }

    private static GpuRuntimeProductionOptimizerGate evaluate(
            String optimizationProfile,
            java.util.Optional<net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact> artifact,
            GpuRuntimeIrOptimizationReport optimizationReport,
            GpuRuntimeEquivalenceEvidence runtimeEquivalenceEvidence,
            GpuRuntimeFallbackEvidence fallbackEvidence
    ) {
        String profile = normalize(optimizationProfile, "off");
        boolean productionRequested = GpuRuntimeProductionProfiles.isProductionProfile(profile);
        GpuRuntimeEquivalenceEvidence equivalence = runtimeEquivalenceEvidence;
        GpuRuntimeFallbackEvidence fallback = fallbackEvidence == null ? GpuRuntimeFallbackEvidence.none() : fallbackEvidence;
        GpuRuntimeIrOptimizationReport report = optimizationReport == null
                ? GpuRuntimeIrOptimizationReport.empty(artifact)
                : optimizationReport;

        boolean runtimeEquivalencePassed = equivalence.executed() && equivalence.equivalent();
        boolean fallbackClean = GpuRuntimeFallbackEvidence.NONE.equals(fallback.decision());
        boolean strategyEvidenceBacked = report.strategyDecision().evidenceBacked() && !report.strategyDecision().advisoryOnly();
        boolean vendorPromotionEligible = report.strategyDecision().vendorBaseline().promotionEligible();
        boolean acceptedProofEvidencePresent = hasAcceptedProofEvidence(report);
        boolean rollbackClean = !report.requiresRollback();
        List<String> diagnostics = new ArrayList<>();

        if (!productionRequested) {
            diagnostics.add("profile does not request production optimizer promotion");
            return new GpuRuntimeProductionOptimizerGate(
                    "not-requested",
                    profile,
                    false,
                    true,
                    runtimeEquivalencePassed,
                    fallbackClean,
                    strategyEvidenceBacked,
                    vendorPromotionEligible,
                    rollbackClean,
                    diagnostics
            );
        }

        if (!runtimeEquivalencePassed) {
            diagnostics.add("runtime equivalence must execute and pass before production promotion");
        }
        if (!fallbackClean) {
            diagnostics.add("fallback evidence must be clean before production promotion");
        }
        if (!strategyEvidenceBacked) {
            diagnostics.add("optimization strategy must be evidence-backed and non-advisory");
        }
        if (!vendorPromotionEligible) {
            diagnostics.add("vendor baseline is not promotion-eligible under A1/A2 gates");
        }
        if (!acceptedProofEvidencePresent) {
            diagnostics.add("accepted optimizer proof artifact is required before production promotion");
        }
        if (!rollbackClean) {
            diagnostics.add("optimizer rollback/failure reports block production promotion");
        }

        String status = diagnostics.isEmpty() ? "accepted" : "blocked";
        if (diagnostics.isEmpty()) {
            diagnostics.add("all production optimizer gates passed");
        }
        return new GpuRuntimeProductionOptimizerGate(
                status,
                profile,
                true,
                true,
                runtimeEquivalencePassed,
                fallbackClean,
                strategyEvidenceBacked,
                vendorPromotionEligible,
                rollbackClean,
                diagnostics
        );
    }

    public boolean accepted() {
        return "accepted".equals(status);
    }

    private static boolean hasAcceptedProofEvidence(GpuRuntimeIrOptimizationReport report) {
        return report.passReports().stream()
                .map(GpuRuntimeIrOptimizationPassReport::proofArtifact)
                .filter(GpuRuntimeProductionOptimizerGate::hasProofArtifact)
                .anyMatch(proofArtifact -> isAcceptedVerdict(proofArtifact.verdict()));
    }

    private static boolean hasProofArtifact(GpuRuntimeIrOptimizationProofArtifact proofArtifact) {
        return proofArtifact != null
                && (!"none".equals(proofArtifact.source()) || !proofArtifact.fields().isEmpty());
    }

    private static boolean isAcceptedVerdict(String verdict) {
        String normalized = verdict == null ? "" : verdict.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("accepted") || normalized.contains("passed") || normalized.contains("ready");
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(status).append('\n');
        builder.append("optimizationProfile=").append(optimizationProfile).append('\n');
        builder.append("productionProfileRequested=").append(productionProfileRequested).append('\n');
        builder.append("runtimeEquivalenceRequired=").append(runtimeEquivalenceRequired).append('\n');
        builder.append("runtimeEquivalencePassed=").append(runtimeEquivalencePassed).append('\n');
        builder.append("fallbackClean=").append(fallbackClean).append('\n');
        builder.append("strategyEvidenceBacked=").append(strategyEvidenceBacked).append('\n');
        builder.append("vendorPromotionEligible=").append(vendorPromotionEligible).append('\n');
        builder.append("rollbackClean=").append(rollbackClean).append('\n');
        builder.append("diagnostic.count=").append(diagnostics.size()).append('\n');
        for (int index = 0; index < diagnostics.size(); index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics.get(index)).append('\n');
        }
        return builder.toString();
    }

    public String toLine() {
        return "productionGate="
                + status
                + " profile="
                + optimizationProfile
                + " requested="
                + productionProfileRequested
                + " runtimeEquivalencePassed="
                + runtimeEquivalencePassed
                + " fallbackClean="
                + fallbackClean
                + " strategyEvidenceBacked="
                + strategyEvidenceBacked
                + " vendorPromotionEligible="
                + vendorPromotionEligible
                + " rollbackClean="
                + rollbackClean
                + " diagnostics="
                + String.join(" | ", diagnostics);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback, "fallback") : value;
    }
}
