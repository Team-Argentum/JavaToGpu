package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        builder.append("manifest.resourcePath=")
                .append(propertyValue(resourcePathFor(normalizedProposal, normalizedRequest)))
                .append('\n');
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
        RuntimeEquivalencePayloadBinding payloadBinding = RuntimeEquivalencePayloadBinding.from(proof);
        builder.append("binding.runtimeEquivalencePayload.required=")
                .append(payloadBinding.required()).append('\n');
        builder.append("binding.runtimeEquivalencePayload.present=")
                .append(payloadBinding.present()).append('\n');
        builder.append("binding.runtimeEquivalencePayload.passed=")
                .append(payloadBinding.passed()).append('\n');
        builder.append("binding.runtimeEquivalencePayload.componentsComplete=")
                .append(payloadBinding.componentsComplete()).append('\n');
        builder.append("binding.runtimeEquivalencePayload.caseCount=")
                .append(payloadBinding.caseCount()).append('\n');
        builder.append("binding.runtimeEquivalencePayload.resource=")
                .append(propertyValue(payloadBinding.resource())).append('\n');
        builder.append("binding.runtimeEquivalencePayload.comparisonMode=")
                .append(propertyValue(payloadBinding.comparisonMode())).append('\n');
        builder.append("binding.rollback.required=true\n");
        builder.append("authorization.productionMutation=disabled\n");
        builder.append("authorization.scope=manual-review-only\n");
        builder.append("diagnostic=complete approval fields and set status=approved; validation remains review-only\n");
        return builder.toString();
    }

    public static String resourceName(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        GpuIrOptimizationProposal normalizedProposal = requireProposalCandidate(proposal);
        GpuIrOptimizationProposalRequest normalizedRequest = normalizeRequest(request, normalizedProposal);
        return resourceNameFor(normalizedProposal, normalizedRequest);
    }

    public static String resourcePath(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        return RESOURCE_DIRECTORY + resourceName(proposal, request);
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
        String manifestResourcePath = normalizedProposal == null
                ? "missing"
                : resourcePathFor(normalizedProposal, normalizedRequest);
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "manifest.resourcePath",
                manifestResourcePath,
                "manifest-resource-path-mismatch"
        );
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
        RuntimeEquivalencePayloadBinding payloadBinding = RuntimeEquivalencePayloadBinding.from(proof);
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.runtimeEquivalencePayload.required",
                Boolean.toString(payloadBinding.required()),
                "manifest-runtime-equivalence-payload-required-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.runtimeEquivalencePayload.present",
                Boolean.toString(payloadBinding.present()),
                "manifest-runtime-equivalence-payload-present-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.runtimeEquivalencePayload.passed",
                Boolean.toString(payloadBinding.passed()),
                "manifest-runtime-equivalence-payload-passed-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.runtimeEquivalencePayload.componentsComplete",
                Boolean.toString(payloadBinding.componentsComplete()),
                "manifest-runtime-equivalence-payload-components-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.runtimeEquivalencePayload.caseCount",
                Integer.toString(payloadBinding.caseCount()),
                "manifest-runtime-equivalence-payload-case-count-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.runtimeEquivalencePayload.resource",
                payloadBinding.resource(),
                "manifest-runtime-equivalence-payload-resource-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.runtimeEquivalencePayload.comparisonMode",
                payloadBinding.comparisonMode(),
                "manifest-runtime-equivalence-payload-comparison-mode-mismatch"
        );
        if (payloadBinding.required() && !payloadBinding.approvalReady()) {
            blockers.add(payloadBinding.firstBlocker());
        }
        requireEquals(blockers, normalizedManifest, "binding.rollback.required", "true", "manifest-rollback-required-mismatch");
        requireEquals(blockers, normalizedManifest, "authorization.productionMutation", "disabled", "manifest-production-mutation-not-disabled");
        requireEquals(blockers, normalizedManifest, "authorization.scope", "manual-review-only", "manifest-authorization-scope-mismatch");

        boolean valid = blockers.isEmpty();
        return new Validation(
                valid,
                valid ? "approved" : "blocked",
                manifestResourcePath,
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
                payloadBinding.required(),
                payloadBinding.present(),
                payloadBinding.passed(),
                payloadBinding.componentsComplete(),
                payloadBinding.caseCount(),
                payloadBinding.resource(),
                payloadBinding.comparisonMode(),
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

    private static String resourcePathFor(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        return RESOURCE_DIRECTORY + resourceNameFor(proposal, request);
    }

    private static String resourceNameFor(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        return "approval-" + sha256Hex(resourceFingerprint(proposal, request)).substring(0, 24) + ".properties";
    }

    private static String resourceFingerprint(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        RuntimeEquivalencePayloadBinding payloadBinding = RuntimeEquivalencePayloadBinding.from(proposal.proofArtifact());
        return String.join(
                "\n",
                SCOPE,
                propertyValue(proposal.optimizerId()),
                propertyValue(proposal.optimizerVersion()),
                propertyValue(proposal.originalIdentity()),
                propertyValue(proposal.optimizedIdentity()),
                context(request, "backendTarget", "UNKNOWN"),
                propertyValue(request.optimizerProfile()),
                context(request, "deviceProfile.vendor", "unknown"),
                context(request, "deviceProfile.label", "unknown"),
                propertyValue(proposal.proofArtifact().source()),
                propertyValue(proposal.proofArtifact().verdict()),
                Boolean.toString(payloadBinding.required()),
                Boolean.toString(payloadBinding.present()),
                Boolean.toString(payloadBinding.passed()),
                Boolean.toString(payloadBinding.componentsComplete()),
                Integer.toString(payloadBinding.caseCount()),
                propertyValue(payloadBinding.resource()),
                propertyValue(payloadBinding.comparisonMode())
        );
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                builder.append(String.format(java.util.Locale.ROOT, "%02x", part & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for optimizer approval manifest resources", exception);
        }
    }

    private static String context(GpuIrOptimizationProposalRequest request, String key, String fallback) {
        return propertyValue(request.contextFields().getOrDefault(key, fallback));
    }

    private static String propertyValue(String value) {
        return normalize(value, "unknown").replace('\\', '/').replace('\r', ' ').replace('\n', ' ');
    }

    private static boolean proofBoolean(Map<String, String> fields, String key) {
        return "true".equalsIgnoreCase(fields.getOrDefault(key, "false"));
    }

    private static int proofInt(Map<String, String> fields, String key) {
        try {
            return Math.max(0, Integer.parseInt(fields.getOrDefault(key, "0")));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Validation(
            boolean valid,
            String status,
            String manifestResourcePath,
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
            boolean runtimeEquivalencePayloadRequired,
            boolean runtimeEquivalencePayloadPresent,
            boolean runtimeEquivalencePayloadPassed,
            boolean runtimeEquivalencePayloadComponentsComplete,
            int runtimeEquivalencePayloadCaseCount,
            String runtimeEquivalencePayloadResource,
            String runtimeEquivalencePayloadComparisonMode,
            List<String> blockers
    ) {
        public Validation {
            status = normalize(status, valid ? "approved" : "blocked");
            manifestResourcePath = normalize(manifestResourcePath, "missing");
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
            runtimeEquivalencePayloadCaseCount = Math.max(0, runtimeEquivalencePayloadCaseCount);
            runtimeEquivalencePayloadResource = normalize(runtimeEquivalencePayloadResource, "not-required");
            runtimeEquivalencePayloadComparisonMode = normalize(runtimeEquivalencePayloadComparisonMode, "not-required");
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
            builder.append("manifest.resourcePath=").append(propertyValue(manifestResourcePath)).append('\n');
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
            builder.append("binding.runtimeEquivalencePayload.required=").append(runtimeEquivalencePayloadRequired).append('\n');
            builder.append("binding.runtimeEquivalencePayload.present=").append(runtimeEquivalencePayloadPresent).append('\n');
            builder.append("binding.runtimeEquivalencePayload.passed=").append(runtimeEquivalencePayloadPassed).append('\n');
            builder.append("binding.runtimeEquivalencePayload.componentsComplete=")
                    .append(runtimeEquivalencePayloadComponentsComplete).append('\n');
            builder.append("binding.runtimeEquivalencePayload.caseCount=").append(runtimeEquivalencePayloadCaseCount).append('\n');
            builder.append("binding.runtimeEquivalencePayload.resource=")
                    .append(propertyValue(runtimeEquivalencePayloadResource)).append('\n');
            builder.append("binding.runtimeEquivalencePayload.comparisonMode=")
                    .append(propertyValue(runtimeEquivalencePayloadComparisonMode)).append('\n');
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

    private record RuntimeEquivalencePayloadBinding(
            boolean required,
            boolean present,
            boolean passed,
            boolean componentsComplete,
            int caseCount,
            String resource,
            String comparisonMode
    ) {
        private static RuntimeEquivalencePayloadBinding from(GpuRuntimeIrOptimizationProofArtifact proof) {
            Map<String, String> fields = proof == null ? Map.of() : proof.fields();
            boolean required = proofBoolean(fields, "proof.runtimeEquivalencePayloadRequiredBeforeSelection")
                    || proofBoolean(fields, "runtimeEquivalencePayload.required");
            boolean present = proofBoolean(fields, "runtimeEquivalencePayload.present");
            boolean passed = proofBoolean(fields, "runtimeEquivalencePayload.passed");
            boolean componentsComplete = proofBoolean(fields, "runtimeEquivalencePayload.cpuReference.present")
                    && proofBoolean(fields, "runtimeEquivalencePayload.preOptimizationOutput.present")
                    && proofBoolean(fields, "runtimeEquivalencePayload.postOptimizationOutput.present")
                    && proofBoolean(fields, "runtimeEquivalencePayload.tolerance.present")
                    && proofBoolean(fields, "runtimeEquivalencePayload.failureFixture.present");
            int caseCount = proofInt(fields, "runtimeEquivalencePayload.Case.Count");
            return new RuntimeEquivalencePayloadBinding(
                    required,
                    present,
                    passed,
                    componentsComplete,
                    caseCount,
                    required ? fields.getOrDefault("runtimeEquivalencePayload.resource", "missing") : "not-required",
                    required ? fields.getOrDefault("runtimeEquivalencePayload.comparisonMode", "missing") : "not-required"
            );
        }

        private boolean approvalReady() {
            return !required || (present && passed && componentsComplete && caseCount > 0);
        }

        private String firstBlocker() {
            if (!required) {
                return "none";
            }
            if (!present) {
                return "runtime-equivalence-payload-missing";
            }
            if (!passed) {
                return "runtime-equivalence-payload-not-passed";
            }
            if (!componentsComplete) {
                return "runtime-equivalence-payload-components-incomplete";
            }
            if (caseCount <= 0) {
                return "runtime-equivalence-payload-cases-missing";
            }
            return "none";
        }
    }
}
