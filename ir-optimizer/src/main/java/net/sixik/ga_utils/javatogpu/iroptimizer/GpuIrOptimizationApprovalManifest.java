package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Manual approval template and validator for one immutable optimizer proposal.
 *
 * <p>This artifact is deliberately review-only. A valid manifest proves that an operator reviewed a specific
 * optimizer proposal identity, but it does not select optimized IR or grant production mutation by itself.</p>
 */
public final class GpuIrOptimizationApprovalManifest {

    public static final String SCOPE = "irgpu-optimization-proposal-manual-approval";
    public static final String RESOURCE_DIRECTORY = "META-INF/javatogpu/ir-optimization-approvals/";
    private static final String REQUIRED = "REQUIRED";

    private GpuIrOptimizationApprovalManifest() {
    }

    public static String template(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        GpuIrOptimizationProposal normalizedProposal = requireProposalCandidate(proposal);
        GpuIrOptimizationProposalRequest normalizedRequest = normalizeRequest(request, normalizedProposal);
        GpuRuntimeIrOptimizationProofArtifact proof = normalizedProposal.proofArtifact();
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=pending\n");
        builder.append("scope=").append(SCOPE).append('\n');
        builder.append("approval.id=").append(REQUIRED).append('\n');
        builder.append("approval.approvedBy=").append(REQUIRED).append('\n');
        builder.append("approval.approvedAtUtc=").append(REQUIRED).append('\n');
        builder.append("binding.optimizerId=").append(propertyValue(normalizedProposal.optimizerId())).append('\n');
        builder.append("binding.optimizerVersion=").append(propertyValue(normalizedProposal.optimizerVersion())).append('\n');
        builder.append("binding.originalIrIdentity=").append(propertyValue(normalizedProposal.originalIdentity())).append('\n');
        builder.append("binding.optimizedIrIdentity=").append(propertyValue(normalizedProposal.optimizedIdentity())).append('\n');
        builder.append("binding.backendTarget=").append(context(normalizedRequest, "backendTarget", "UNKNOWN")).append('\n');
        builder.append("binding.optimizationProfile=").append(propertyValue(normalizedRequest.optimizerProfile())).append('\n');
        builder.append("binding.deviceVendor=").append(context(normalizedRequest, "deviceProfile.vendor", "unknown")).append('\n');
        builder.append("binding.deviceLabel=").append(context(normalizedRequest, "deviceProfile.label", "unknown")).append('\n');
        builder.append("binding.proof.source=").append(propertyValue(proof.source())).append('\n');
        builder.append("binding.proof.verdict=").append(propertyValue(proof.verdict())).append('\n');
        builder.append("binding.rollback.required=true\n");
        builder.append("authorization.productionMutation=disabled\n");
        builder.append("authorization.scope=manual-review-only\n");
        builder.append("diagnostic=complete approval fields and set status=approved; validation remains review-only\n");
        return builder.toString();
    }

    public static Validation validate(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request,
            Properties manifest
    ) {
        GpuIrOptimizationProposal normalizedProposal = proposal;
        GpuIrOptimizationProposalRequest normalizedRequest = normalizeRequest(request, proposal);
        Properties normalizedManifest = manifest == null ? new Properties() : manifest;
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        if (normalizedProposal == null) {
            blockers.add("proposal-missing");
        } else {
            blockers.addAll(proposalBlockers(normalizedProposal));
        }

        String approvalId = normalize(normalizedManifest.getProperty("approval.id"), "approval:missing");
        String approvedBy = normalize(normalizedManifest.getProperty("approval.approvedBy"), "missing");
        String approvedAtUtc = normalize(normalizedManifest.getProperty("approval.approvedAtUtc"), "missing");

        requireEquals(blockers, normalizedManifest, "formatVersion", "1", "manifest-format-version-mismatch");
        requireEquals(blockers, normalizedManifest, "status", "approved", "manifest-not-approved");
        requireEquals(blockers, normalizedManifest, "scope", SCOPE, "manifest-scope-mismatch");
        requireApprovalValue(blockers, approvalId, "manifest-approval-id-missing");
        requireApprovalValue(blockers, approvedBy, "manifest-approved-by-missing");
        requireApprovalValue(blockers, approvedAtUtc, "manifest-approved-at-missing");
        if (!REQUIRED.equals(approvedAtUtc) && !"missing".equals(approvedAtUtc)) {
            try {
                Instant.parse(approvedAtUtc);
            } catch (DateTimeParseException exception) {
                blockers.add("manifest-approved-at-invalid");
            }
        }

        String optimizerId = normalizedProposal == null ? "missing" : normalizedProposal.optimizerId();
        String optimizerVersion = normalizedProposal == null ? "missing" : normalizedProposal.optimizerVersion();
        String originalIdentity = normalizedProposal == null ? "missing" : normalizedProposal.originalIdentity();
        String optimizedIdentity = normalizedProposal == null ? "missing" : normalizedProposal.optimizedIdentity();
        GpuRuntimeIrOptimizationProofArtifact proof = normalizedProposal == null
                ? GpuRuntimeIrOptimizationProofArtifact.fromFields("missing", "missing", Map.of())
                : normalizedProposal.proofArtifact();

        requireBindingEquals(blockers, normalizedManifest, "binding.optimizerId", optimizerId, "manifest-optimizer-id-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.optimizerVersion", optimizerVersion, "manifest-optimizer-version-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.originalIrIdentity", originalIdentity, "manifest-original-ir-identity-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.optimizedIrIdentity", optimizedIdentity, "manifest-optimized-ir-identity-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.backendTarget", context(normalizedRequest, "backendTarget", "UNKNOWN"), "manifest-backend-target-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.optimizationProfile", normalizedRequest.optimizerProfile(), "manifest-optimization-profile-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.deviceVendor", context(normalizedRequest, "deviceProfile.vendor", "unknown"), "manifest-device-vendor-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.deviceLabel", context(normalizedRequest, "deviceProfile.label", "unknown"), "manifest-device-label-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.proof.source", proof.source(), "manifest-proof-source-mismatch");
        requireBindingEquals(blockers, normalizedManifest, "binding.proof.verdict", proof.verdict(), "manifest-proof-verdict-mismatch");
        requireEquals(blockers, normalizedManifest, "binding.rollback.required", "true", "manifest-rollback-required-mismatch");
        requireEquals(blockers, normalizedManifest, "authorization.productionMutation", "disabled", "manifest-production-mutation-not-disabled");
        requireEquals(blockers, normalizedManifest, "authorization.scope", "manual-review-only", "manifest-authorization-scope-mismatch");

        boolean valid = blockers.isEmpty();
        return new Validation(
                valid,
                valid ? "approved" : "blocked",
                approvalId,
                approvedBy,
                approvedAtUtc,
                optimizerId,
                optimizerVersion,
                originalIdentity,
                optimizedIdentity,
                context(normalizedRequest, "backendTarget", "UNKNOWN"),
                normalizedRequest.optimizerProfile(),
                context(normalizedRequest, "deviceProfile.vendor", "unknown"),
                context(normalizedRequest, "deviceProfile.label", "unknown"),
                proof.source(),
                proof.verdict(),
                List.copyOf(blockers)
        );
    }

    private static GpuIrOptimizationProposal requireProposalCandidate(GpuIrOptimizationProposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("Optimizer approval manifest requires a proposal");
        }
        List<String> blockers = proposalBlockers(proposal);
        if (!blockers.isEmpty()) {
            throw new IllegalStateException("Cannot create optimizer approval manifest template: " + blockers.get(0));
        }
        return proposal;
    }

    private static List<String> proposalBlockers(GpuIrOptimizationProposal proposal) {
        ArrayList<String> blockers = new ArrayList<>();
        if (proposal.decision() != GpuIrOptimizationProposalDecision.PROPOSED) {
            blockers.add("proposal-decision-not-proposed");
        }
        if (!proposal.hasOptimizedArtifact()) {
            blockers.add("proposal-optimized-artifact-missing");
        }
        if (proposal.originalIdentity().equals(proposal.optimizedIdentity())) {
            blockers.add("proposal-identities-not-distinct");
        }
        GpuRuntimeIrOptimizationProofArtifact proof = proposal.proofArtifact();
        if (proof.source().isBlank() || "none".equals(proof.source()) || "ir-optimizer".equals(proof.source())) {
            blockers.add("proposal-proof-source-not-specific");
        }
        String verdict = proof.verdict().toLowerCase(java.util.Locale.ROOT);
        if (verdict.isBlank()
                || verdict.contains("not-proven")
                || verdict.contains("rejected")
                || verdict.contains("failed")) {
            blockers.add("proposal-proof-verdict-not-accepted");
        }
        return List.copyOf(blockers);
    }

    private static GpuIrOptimizationProposalRequest normalizeRequest(
            GpuIrOptimizationProposalRequest request,
            GpuIrOptimizationProposal proposal
    ) {
        if (request != null) {
            return request;
        }
        if (proposal == null) {
            throw new IllegalArgumentException("Optimizer approval manifest requires a request when proposal is missing");
        }
        return new GpuIrOptimizationProposalRequest(proposal.originalArtifact());
    }

    private static void requireEquals(
            LinkedHashSet<String> blockers,
            Properties properties,
            String key,
            String expected,
            String blocker
    ) {
        if (!expected.equals(properties.getProperty(key))) {
            blockers.add(blocker);
        }
    }

    private static void requireBindingEquals(
            LinkedHashSet<String> blockers,
            Properties manifest,
            String key,
            String expected,
            String blocker
    ) {
        if (!normalize(expected, "unknown").equals(normalize(manifest.getProperty(key), "missing"))) {
            blockers.add(blocker);
        }
    }

    private static void requireApprovalValue(LinkedHashSet<String> blockers, String value, String blocker) {
        if (value.isBlank() || REQUIRED.equals(value) || "missing".equals(value) || "approval:missing".equals(value)) {
            blockers.add(blocker);
        }
    }

    private static String context(GpuIrOptimizationProposalRequest request, String key, String fallback) {
        return propertyValue(request.contextFields().getOrDefault(key, fallback));
    }

    private static String propertyValue(String value) {
        return normalize(value, "unknown").replace('\\', '/').replace('\r', ' ').replace('\n', ' ');
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Validation(
            boolean valid,
            String status,
            String approvalId,
            String approvedBy,
            String approvedAtUtc,
            String optimizerId,
            String optimizerVersion,
            String originalIrIdentity,
            String optimizedIrIdentity,
            String backendTarget,
            String optimizationProfile,
            String deviceVendor,
            String deviceLabel,
            String proofSource,
            String proofVerdict,
            List<String> blockers
    ) {
        public Validation {
            status = normalize(status, valid ? "approved" : "blocked");
            approvalId = normalize(approvalId, "approval:missing");
            approvedBy = normalize(approvedBy, "missing");
            approvedAtUtc = normalize(approvedAtUtc, "missing");
            optimizerId = normalize(optimizerId, "optimizer:missing");
            optimizerVersion = normalize(optimizerVersion, "missing");
            originalIrIdentity = normalize(originalIrIdentity, "irgpu:missing");
            optimizedIrIdentity = normalize(optimizedIrIdentity, "irgpu:missing");
            backendTarget = normalize(backendTarget, "UNKNOWN");
            optimizationProfile = normalize(optimizationProfile, "off");
            deviceVendor = normalize(deviceVendor, "unknown");
            deviceLabel = normalize(deviceLabel, "unknown");
            proofSource = normalize(proofSource, "missing");
            proofVerdict = normalize(proofVerdict, "missing");
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            if (valid && !blockers.isEmpty()) {
                throw new IllegalArgumentException("Valid optimizer approval manifest must not contain blockers");
            }
        }

        public String firstBlocker() {
            return blockers.isEmpty() ? "none" : blockers.get(0);
        }

        public String toPropertiesText() {
            StringBuilder builder = new StringBuilder();
            builder.append("formatVersion=1\n");
            builder.append("status=").append(status).append('\n');
            builder.append("valid=").append(valid).append('\n');
            builder.append("scope=").append(SCOPE).append("-validation\n");
            builder.append("approval.id=").append(propertyValue(approvalId)).append('\n');
            builder.append("approval.approvedBy=").append(propertyValue(approvedBy)).append('\n');
            builder.append("approval.approvedAtUtc=").append(propertyValue(approvedAtUtc)).append('\n');
            builder.append("binding.optimizerId=").append(propertyValue(optimizerId)).append('\n');
            builder.append("binding.optimizerVersion=").append(propertyValue(optimizerVersion)).append('\n');
            builder.append("binding.originalIrIdentity=").append(propertyValue(originalIrIdentity)).append('\n');
            builder.append("binding.optimizedIrIdentity=").append(propertyValue(optimizedIrIdentity)).append('\n');
            builder.append("binding.backendTarget=").append(propertyValue(backendTarget)).append('\n');
            builder.append("binding.optimizationProfile=").append(propertyValue(optimizationProfile)).append('\n');
            builder.append("binding.deviceVendor=").append(propertyValue(deviceVendor)).append('\n');
            builder.append("binding.deviceLabel=").append(propertyValue(deviceLabel)).append('\n');
            builder.append("binding.proof.source=").append(propertyValue(proofSource)).append('\n');
            builder.append("binding.proof.verdict=").append(propertyValue(proofVerdict)).append('\n');
            builder.append("authorization.productionMutation=disabled\n");
            builder.append("authorization.scope=manual-review-only\n");
            builder.append("blocker.count=").append(blockers.size()).append('\n');
            for (int index = 0; index < blockers.size(); index++) {
                builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
            }
            builder.append("diagnostic=").append(valid
                    ? "optimizer approval manifest is valid for the reviewed proposal; selection remains external"
                    : "optimizer approval manifest is blocked by " + firstBlocker()).append('\n');
            return builder.toString();
        }
    }
}
