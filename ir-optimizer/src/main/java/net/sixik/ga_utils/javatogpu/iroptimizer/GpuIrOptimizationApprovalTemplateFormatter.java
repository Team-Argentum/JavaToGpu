package net.sixik.ga_utils.javatogpu.iroptimizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formats review-only approval templates for optimizer proposals without changing runtime selection.
 */
public final class GpuIrOptimizationApprovalTemplateFormatter {

    private GpuIrOptimizationApprovalTemplateFormatter() {
    }

    public static GpuIrOptimizationApprovalTemplateResult format(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        LinkedHashMap<String, String> fields = baseFields(proposal);
        if (proposal == null) {
            fields.put("diagnostic", "optimizer approval template skipped because proposal is missing");
            return GpuIrOptimizationApprovalTemplateResult.notApplicable("proposal-missing", fields);
        }
        if (proposal.decision() != GpuIrOptimizationProposalDecision.PROPOSED) {
            fields.put("diagnostic", "optimizer approval template skipped because proposal decision is " + proposal.decision());
            return GpuIrOptimizationApprovalTemplateResult.notApplicable("proposal-decision-not-proposed", fields);
        }
        try {
            String template = GpuIrOptimizationApprovalManifest.template(proposal, request);
            fields.put("diagnostic", "optimizer approval template is pending manual review");
            fields.put("resourceDirectory", GpuIrOptimizationApprovalManifest.RESOURCE_DIRECTORY);
            fields.put("resourcePath", GpuIrOptimizationApprovalManifest.resourcePath(proposal, request));
            return GpuIrOptimizationApprovalTemplateResult.pending(template, fields);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            String blocker = firstBlocker(failure);
            fields.put("diagnostic", failure.getMessage());
            return GpuIrOptimizationApprovalTemplateResult.notApplicable(blocker, fields);
        }
    }

    private static LinkedHashMap<String, String> baseFields(GpuIrOptimizationProposal proposal) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("scope", GpuIrOptimizationApprovalManifest.SCOPE);
        fields.put("optimizerId", proposal == null ? "missing" : proposal.optimizerId());
        fields.put("optimizerVersion", proposal == null ? "missing" : proposal.optimizerVersion());
        fields.put("decision", proposal == null ? "missing" : proposal.decision().name());
        fields.put("hasOptimizedArtifact", Boolean.toString(proposal != null && proposal.hasOptimizedArtifact()));
        Map<String, String> proofFields = proposal == null ? Map.of() : proposal.proofArtifact().fields();
        boolean runtimeEquivalencePayloadRequired = fieldIsTrue(
                proofFields,
                "proof.runtimeEquivalencePayloadRequiredBeforeSelection"
        ) || fieldIsTrue(proofFields, "runtimeEquivalencePayload.required");
        boolean runtimeEquivalencePayloadComponentsComplete = fieldIsTrue(
                proofFields,
                "runtimeEquivalencePayload.cpuReference.present"
        ) && fieldIsTrue(proofFields, "runtimeEquivalencePayload.preOptimizationOutput.present")
                && fieldIsTrue(proofFields, "runtimeEquivalencePayload.postOptimizationOutput.present")
                && fieldIsTrue(proofFields, "runtimeEquivalencePayload.tolerance.present")
                && fieldIsTrue(proofFields, "runtimeEquivalencePayload.failureFixture.present");
        fields.put("runtimeEquivalencePayload.required", Boolean.toString(runtimeEquivalencePayloadRequired));
        fields.put(
                "runtimeEquivalencePayload.present",
                Boolean.toString(fieldIsTrue(proofFields, "runtimeEquivalencePayload.present"))
        );
        fields.put(
                "runtimeEquivalencePayload.passed",
                Boolean.toString(fieldIsTrue(proofFields, "runtimeEquivalencePayload.passed"))
        );
        fields.put(
                "runtimeEquivalencePayload.componentsComplete",
                Boolean.toString(runtimeEquivalencePayloadComponentsComplete)
        );
        fields.put(
                "runtimeEquivalencePayload.caseCount",
                proofFields.getOrDefault("runtimeEquivalencePayload.Case.Count", "0")
        );
        fields.put(
                "runtimeEquivalencePayload.resource",
                runtimeEquivalencePayloadRequired
                        ? proofFields.getOrDefault("runtimeEquivalencePayload.resource", "missing")
                        : "not-required"
        );
        fields.put(
                "runtimeEquivalencePayload.comparisonMode",
                runtimeEquivalencePayloadRequired
                        ? proofFields.getOrDefault("runtimeEquivalencePayload.comparisonMode", "missing")
                        : "not-required"
        );
        fields.put("productionMutation", "disabled");
        fields.put("manualReviewOnly", "true");
        return fields;
    }

    private static boolean fieldIsTrue(Map<String, String> fields, String key) {
        return "true".equalsIgnoreCase(fields.getOrDefault(key, "false"));
    }

    private static String firstBlocker(Throwable failure) {
        String message = failure == null ? "unknown" : failure.getMessage();
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        int separator = message.lastIndexOf(": ");
        return separator >= 0 && separator + 2 < message.length()
                ? message.substring(separator + 2)
                : message;
    }
}
