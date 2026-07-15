package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;

/**
 * Read-only binding preview between a future optimized artifact envelope and proof/review evidence.
 *
 * <p>The preview records which proof, runtime-equivalence, rollback, and approval anchors are still missing before a
 * future optimized artifact could be selected. It never binds proof artifacts, builds optimized IR, or enables
 * selection.</p>
 */
public record GpuRuntimeIrPeepholeArtifactProofBindingPreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        String originalIrIdentity,
        String optimizedArtifactIdentity,
        String envelopeKey,
        String proofAnchor,
        String rollbackAnchor,
        String proofStatus,
        String proofFirstBlocker,
        String reviewPackageStatus,
        String reviewPackageFirstBlocker,
        boolean artifactEnvelopeReady,
        boolean proofRequired,
        boolean proofAccepted,
        boolean runtimeEquivalencePayloadComplete,
        boolean rollbackEvidencePresent,
        boolean rollbackClean,
        boolean approvalAccepted,
        boolean reviewPackageComplete,
        boolean bindingReady,
        String status,
        String firstBlocker,
        boolean bindingPreflightImplemented,
        boolean proofBound,
        boolean rollbackBound,
        boolean approvalBound,
        boolean optimizedArtifactBuilt,
        boolean transformedIrBuilt,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    private static final String RULE_UNKNOWN = "rule:unknown";
    private static final String UNKNOWN = "unknown";
    private static final String ORIGINAL_IR_UNKNOWN = "irgpu:unknown";
    private static final String NOT_BUILT = "not-built";
    private static final String STATUS_BLOCKED = "blocked";
    private static final String STATUS_NOT_REQUIRED = "not-required";
    private static final String STATUS_BINDING_READY = "binding-ready";
    private static final String BLOCKER_NONE = "none";
    private static final String BLOCKER_RUNTIME_EQUIVALENCE_PAYLOAD_MISSING = "runtime-equivalence-payload-missing";
    private static final String BLOCKER_NO_PROOF_CANDIDATES = "no-proof-candidates";
    private static final String BLOCKER_NO_REVIEW_CANDIDATES = "no-review-candidates";
    private static final String BLOCKER_ENVELOPE_MISSING = "envelope-missing";
    private static final String BLOCKER_ARTIFACT_PROOF_BINDING_BLOCKED = "artifact-proof-binding-blocked";
    private static final String BLOCKER_PROOF_NOT_REQUIRED_FOR_ENVELOPE = "proof-not-required-for-envelope";
    private static final String BLOCKER_ROLLBACK_EVIDENCE_MISSING = "rollback-evidence-missing";
    private static final String BLOCKER_ROLLBACK_NOT_CLEAN = "rollback-not-clean";
    private static final String BLOCKER_APPROVAL_NOT_ACCEPTED = "approval-not-accepted";
    private static final String BLOCKER_PROOF_NOT_ACCEPTED = "proof-not-accepted";
    private static final String BLOCKER_REVIEW_PACKAGE_MISSING = "review-package-missing";
    private static final String BLOCKER_MANUAL_BINDING_REQUIRED = "manual-binding-required";
    private static final String PROOF_ANCHOR_RUNTIME_EQUIVALENCE_REQUIRED = "runtime-equivalence-required";
    private static final String ROLLBACK_ANCHOR_ORIGINAL_IR = "original-ir";

    public GpuRuntimeIrPeepholeArtifactProofBindingPreflight {
        ruleId = normalize(ruleId, RULE_UNKNOWN);
        methodName = normalize(methodName, UNKNOWN);
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, UNKNOWN);
        originalIrIdentity = normalize(originalIrIdentity, ORIGINAL_IR_UNKNOWN);
        optimizedArtifactIdentity = normalize(optimizedArtifactIdentity, NOT_BUILT);
        envelopeKey = normalize(envelopeKey, NOT_BUILT);
        proofAnchor = normalize(proofAnchor, PROOF_ANCHOR_RUNTIME_EQUIVALENCE_REQUIRED);
        rollbackAnchor = normalize(rollbackAnchor, ROLLBACK_ANCHOR_ORIGINAL_IR);
        proofStatus = normalize(proofStatus, proofRequired ? STATUS_BLOCKED : STATUS_NOT_REQUIRED);
        proofFirstBlocker = normalize(proofFirstBlocker, proofFirstBlockerDefault(proofRequired));
        reviewPackageStatus = normalize(reviewPackageStatus, proofRequired ? STATUS_BLOCKED : STATUS_NOT_REQUIRED);
        reviewPackageFirstBlocker = normalize(reviewPackageFirstBlocker, reviewPackageFirstBlockerDefault(proofRequired));
        status = normalize(status, bindingReady ? STATUS_BINDING_READY : STATUS_BLOCKED);
        firstBlocker = normalize(firstBlocker, bindingReady ? BLOCKER_NONE : BLOCKER_ARTIFACT_PROOF_BINDING_BLOCKED);
    }

    public static GpuRuntimeIrPeepholeArtifactProofBindingPreflight from(
            GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight envelope,
            GpuRuntimeIrPeepholeRewriteProofReadiness proofReadiness,
            GpuRuntimeIrPeepholeRewriteReviewPackage reviewPackage
    ) {
        if (envelope == null) {
            return blocked(
                    RULE_UNKNOWN,
                    UNKNOWN,
                    0,
                    UNKNOWN,
                    ORIGINAL_IR_UNKNOWN,
                    NOT_BUILT,
                    NOT_BUILT,
                    PROOF_ANCHOR_RUNTIME_EQUIVALENCE_REQUIRED,
                    ROLLBACK_ANCHOR_ORIGINAL_IR,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    STATUS_NOT_REQUIRED,
                    BLOCKER_ENVELOPE_MISSING,
                    STATUS_NOT_REQUIRED,
                    BLOCKER_ENVELOPE_MISSING,
                    BLOCKER_ENVELOPE_MISSING
            );
        }
        boolean proofRequired = proofReadiness != null && proofReadiness.proofRequired();
        boolean proofAccepted = proofReadiness != null && proofReadiness.proofAccepted();
        boolean runtimePayloadComplete = proofReadiness != null && proofReadiness.runtimeEquivalencePayloadComplete();
        boolean rollbackEvidencePresent = proofReadiness != null && proofReadiness.rollbackEvidencePresent();
        boolean rollbackClean = proofReadiness != null && proofReadiness.rollbackClean();
        boolean approvalAccepted = proofReadiness != null && proofReadiness.approvalAccepted();
        boolean reviewComplete = reviewPackage != null && reviewPackage.complete();
        boolean bindingReady = envelope.artifactEnvelopeReady()
                && proofRequired
                && proofAccepted
                && runtimePayloadComplete
                && rollbackEvidencePresent
                && rollbackClean
                && approvalAccepted
                && reviewComplete;
        if (!bindingReady) {
            return blocked(
                    envelope.ruleId(),
                    envelope.methodName(),
                    envelope.rootNodeId(),
                    envelope.replacementKind(),
                    envelope.originalIrIdentity(),
                    envelope.optimizedArtifactIdentity(),
                    envelope.envelopeKey(),
                    envelope.proofAnchor(),
                    envelope.rollbackAnchor(),
                    envelope.artifactEnvelopeReady(),
                    proofRequired,
                    proofAccepted,
                    runtimePayloadComplete,
                    rollbackEvidencePresent,
                    rollbackClean,
                    approvalAccepted,
                    reviewComplete,
                    proofReadiness == null ? STATUS_NOT_REQUIRED : proofReadiness.status(),
                    proofReadiness == null ? BLOCKER_NO_PROOF_CANDIDATES : proofReadiness.firstBlocker(),
                    reviewPackage == null ? STATUS_NOT_REQUIRED : reviewPackage.status(),
                    reviewPackage == null ? BLOCKER_NO_REVIEW_CANDIDATES : reviewPackage.firstBlocker(),
                    firstBlocker(envelope, proofReadiness, reviewPackage)
            );
        }
        return new GpuRuntimeIrPeepholeArtifactProofBindingPreflight(
                envelope.ruleId(),
                envelope.methodName(),
                envelope.rootNodeId(),
                envelope.replacementKind(),
                envelope.originalIrIdentity(),
                envelope.optimizedArtifactIdentity(),
                envelope.envelopeKey(),
                envelope.proofAnchor(),
                envelope.rollbackAnchor(),
                proofReadiness.status(),
                proofReadiness.firstBlocker(),
                reviewPackage.status(),
                reviewPackage.firstBlocker(),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                STATUS_BINDING_READY,
                BLOCKER_NONE,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "artifactProofBinding");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".originalIrIdentity", originalIrIdentity),
                Map.entry(normalizedPrefix + ".optimizedArtifactIdentity", optimizedArtifactIdentity),
                Map.entry(normalizedPrefix + ".envelopeKey", envelopeKey),
                Map.entry(normalizedPrefix + ".proofAnchor", proofAnchor),
                Map.entry(normalizedPrefix + ".rollbackAnchor", rollbackAnchor),
                Map.entry(normalizedPrefix + ".proofStatus", proofStatus),
                Map.entry(normalizedPrefix + ".proofFirstBlocker", proofFirstBlocker),
                Map.entry(normalizedPrefix + ".reviewPackageStatus", reviewPackageStatus),
                Map.entry(normalizedPrefix + ".reviewPackageFirstBlocker", reviewPackageFirstBlocker),
                Map.entry(normalizedPrefix + ".artifactEnvelopeReady", Boolean.toString(artifactEnvelopeReady)),
                Map.entry(normalizedPrefix + ".proofRequired", Boolean.toString(proofRequired)),
                Map.entry(normalizedPrefix + ".proofAccepted", Boolean.toString(proofAccepted)),
                Map.entry(normalizedPrefix + ".runtimeEquivalencePayload.complete", Boolean.toString(runtimeEquivalencePayloadComplete)),
                Map.entry(normalizedPrefix + ".rollbackEvidence.present", Boolean.toString(rollbackEvidencePresent)),
                Map.entry(normalizedPrefix + ".rollbackClean", Boolean.toString(rollbackClean)),
                Map.entry(normalizedPrefix + ".approvalAccepted", Boolean.toString(approvalAccepted)),
                Map.entry(normalizedPrefix + ".reviewPackageComplete", Boolean.toString(reviewPackageComplete)),
                Map.entry(normalizedPrefix + ".bindingReady", Boolean.toString(bindingReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".bindingPreflightImplemented", Boolean.toString(bindingPreflightImplemented)),
                Map.entry(normalizedPrefix + ".proofBound", Boolean.toString(proofBound)),
                Map.entry(normalizedPrefix + ".rollbackBound", Boolean.toString(rollbackBound)),
                Map.entry(normalizedPrefix + ".approvalBound", Boolean.toString(approvalBound)),
                Map.entry(normalizedPrefix + ".optimizedArtifactBuilt", Boolean.toString(optimizedArtifactBuilt)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeArtifactProofBindingPreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            String originalIrIdentity,
            String optimizedArtifactIdentity,
            String envelopeKey,
            String proofAnchor,
            String rollbackAnchor,
            boolean artifactEnvelopeReady,
            boolean proofRequired,
            boolean proofAccepted,
            boolean runtimeEquivalencePayloadComplete,
            boolean rollbackEvidencePresent,
            boolean rollbackClean,
            boolean approvalAccepted,
            boolean reviewPackageComplete,
            String proofStatus,
            String proofFirstBlocker,
            String reviewPackageStatus,
            String reviewPackageFirstBlocker,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeArtifactProofBindingPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                originalIrIdentity,
                optimizedArtifactIdentity,
                envelopeKey,
                proofAnchor,
                rollbackAnchor,
                proofStatus,
                proofFirstBlocker,
                reviewPackageStatus,
                reviewPackageFirstBlocker,
                artifactEnvelopeReady,
                proofRequired,
                proofAccepted,
                runtimeEquivalencePayloadComplete,
                rollbackEvidencePresent,
                rollbackClean,
                approvalAccepted,
                reviewPackageComplete,
                false,
                STATUS_BLOCKED,
                firstBlocker,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    private static String firstBlocker(
            GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight envelope,
            GpuRuntimeIrPeepholeRewriteProofReadiness proofReadiness,
            GpuRuntimeIrPeepholeRewriteReviewPackage reviewPackage
    ) {
        if (!envelope.artifactEnvelopeReady()) {
            return envelope.firstBlocker();
        }
        if (proofReadiness == null || !proofReadiness.proofRequired()) {
            return BLOCKER_PROOF_NOT_REQUIRED_FOR_ENVELOPE;
        }
        if (!proofReadiness.runtimeEquivalencePayloadComplete()) {
            return proofReadiness.firstBlocker();
        }
        if (!proofReadiness.rollbackEvidencePresent()) {
            return BLOCKER_ROLLBACK_EVIDENCE_MISSING;
        }
        if (!proofReadiness.rollbackClean()) {
            return BLOCKER_ROLLBACK_NOT_CLEAN;
        }
        if (!proofReadiness.approvalAccepted()) {
            return BLOCKER_APPROVAL_NOT_ACCEPTED;
        }
        if (!proofReadiness.proofAccepted()) {
            return BLOCKER_PROOF_NOT_ACCEPTED;
        }
        if (reviewPackage == null || !reviewPackage.complete()) {
            return reviewPackage == null ? BLOCKER_REVIEW_PACKAGE_MISSING : reviewPackage.firstBlocker();
        }
        return BLOCKER_MANUAL_BINDING_REQUIRED;
    }

    private static String proofFirstBlockerDefault(boolean proofRequired) {
        return proofRequired ? BLOCKER_RUNTIME_EQUIVALENCE_PAYLOAD_MISSING : BLOCKER_NO_PROOF_CANDIDATES;
    }

    private static String reviewPackageFirstBlockerDefault(boolean proofRequired) {
        return proofRequired ? BLOCKER_RUNTIME_EQUIVALENCE_PAYLOAD_MISSING : BLOCKER_NO_REVIEW_CANDIDATES;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
