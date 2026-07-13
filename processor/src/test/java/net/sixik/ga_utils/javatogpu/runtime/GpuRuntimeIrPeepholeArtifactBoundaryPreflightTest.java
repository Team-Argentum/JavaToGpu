package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GpuRuntimeIrPeepholeArtifactBoundaryPreflightTest {

    @Test
    void artifactProofBindingPreflightBlocksWhenEnvelopeIsMissing() {
        GpuRuntimeIrPeepholeArtifactProofBindingPreflight binding =
                GpuRuntimeIrPeepholeArtifactProofBindingPreflight.from(null, null, null);
        Map<String, String> fields = binding.fields("binding");

        assertEquals("blocked", binding.status());
        assertEquals("envelope-missing", binding.firstBlocker());
        assertEquals("rule:unknown", fields.get("binding.ruleId"));
        assertEquals("not-built", fields.get("binding.optimizedArtifactIdentity"));
        assertEquals("not-built", fields.get("binding.envelopeKey"));
        assertEquals("runtime-equivalence-required", fields.get("binding.proofAnchor"));
        assertEquals("original-ir", fields.get("binding.rollbackAnchor"));
        assertEquals("not-required", fields.get("binding.proofStatus"));
        assertEquals("envelope-missing", fields.get("binding.proofFirstBlocker"));
        assertEquals("not-required", fields.get("binding.reviewPackageStatus"));
        assertEquals("envelope-missing", fields.get("binding.reviewPackageFirstBlocker"));
        assertEquals("false", fields.get("binding.artifactEnvelopeReady"));
        assertEquals("false", fields.get("binding.proofRequired"));
        assertEquals("false", fields.get("binding.bindingReady"));
        assertFailClosedBindingFields(fields, "binding");
    }

    @Test
    void artifactProofBindingPreflightBlocksReadyEnvelopeWithoutRequiredProof() {
        GpuRuntimeIrPeepholeArtifactProofBindingPreflight binding =
                GpuRuntimeIrPeepholeArtifactProofBindingPreflight.from(readyEnvelope(), null, null);
        Map<String, String> fields = binding.fields("binding");

        assertEquals("blocked", binding.status());
        assertEquals("proof-not-required-for-envelope", binding.firstBlocker());
        assertEquals("true", fields.get("binding.artifactEnvelopeReady"));
        assertEquals("not-required", fields.get("binding.proofStatus"));
        assertEquals("no-proof-candidates", fields.get("binding.proofFirstBlocker"));
        assertEquals("not-required", fields.get("binding.reviewPackageStatus"));
        assertEquals("no-review-candidates", fields.get("binding.reviewPackageFirstBlocker"));
        assertEquals("false", fields.get("binding.proofRequired"));
        assertEquals("false", fields.get("binding.bindingReady"));
        assertFailClosedBindingFields(fields, "binding");
    }

    @Test
    void artifactSelectionPreflightBlocksWhenProofBindingIsMissing() {
        GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight selection =
                GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight.from(null);
        Map<String, String> fields = selection.fields("selection");

        assertEquals("blocked", selection.status());
        assertEquals("artifact-proof-binding-missing", selection.firstBlocker());
        assertEquals("rule:unknown", fields.get("selection.ruleId"));
        assertEquals("not-built", fields.get("selection.optimizedArtifactIdentity"));
        assertEquals("blocked", fields.get("selection.proofBindingStatus"));
        assertEquals("artifact-proof-binding-missing", fields.get("selection.proofBindingFirstBlocker"));
        assertEquals("false", fields.get("selection.proofBindingReady"));
        assertEquals("true", fields.get("selection.productionGateRequired"));
        assertEquals("false", fields.get("selection.selectionReady"));
        assertFailClosedSelectionFields(fields, "selection");
    }

    @Test
    void artifactSelectionPreflightStillBlocksReadyBindingUntilArtifactsAreBoundAndBuilt() {
        GpuRuntimeIrPeepholeArtifactProofBindingPreflight binding =
                GpuRuntimeIrPeepholeArtifactProofBindingPreflight.from(
                        readyEnvelope(),
                        acceptedProof(),
                        completeReviewPackage()
                );
        GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight selection =
                GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight.from(binding);
        Map<String, String> fields = selection.fields("selection");

        assertEquals("binding-ready", binding.status());
        assertEquals("none", binding.firstBlocker());
        assertEquals("true", binding.fields("binding").get("binding.bindingReady"));
        assertFalse(binding.proofBound());
        assertFalse(binding.optimizedArtifactBuilt());

        assertEquals("blocked", selection.status());
        assertEquals("proof-not-bound", selection.firstBlocker());
        assertEquals("binding-ready", fields.get("selection.proofBindingStatus"));
        assertEquals("none", fields.get("selection.proofBindingFirstBlocker"));
        assertEquals("true", fields.get("selection.proofBindingReady"));
        assertEquals("true", fields.get("selection.productionGateRequired"));
        assertEquals("false", fields.get("selection.selectionReady"));
        assertFailClosedSelectionFields(fields, "selection");
    }

    private static GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight readyEnvelope() {
        return new GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight(
                "madFma",
                "kernel",
                1,
                "mad-fma",
                "irgpu:sha256:original",
                "not-built",
                "not-built",
                "materialization-key",
                "envelope-key",
                "runtime-equivalence-required",
                "original-ir",
                "6",
                List.of(1),
                List.of(1, 2),
                List.of(3, 4, 5),
                List.of(6),
                true,
                true,
                true,
                true,
                true,
                "artifact-envelope-ready",
                "none",
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    private static GpuRuntimeIrPeepholeRewriteProofReadiness acceptedProof() {
        return new GpuRuntimeIrPeepholeRewriteProofReadiness(
                "peephole",
                1,
                "proof-ready",
                "none",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                "irgpu:sha256:original",
                "not-built"
        );
    }

    private static GpuRuntimeIrPeepholeRewriteReviewPackage completeReviewPackage() {
        return new GpuRuntimeIrPeepholeRewriteReviewPackage(
                "peephole",
                1,
                0,
                "ready-for-manual-review",
                "none",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                "irgpu:sha256:original",
                "not-built"
        );
    }

    private static void assertFailClosedBindingFields(Map<String, String> fields, String prefix) {
        assertEquals("true", fields.get(prefix + ".bindingPreflightImplemented"));
        assertEquals("false", fields.get(prefix + ".proofBound"));
        assertEquals("false", fields.get(prefix + ".rollbackBound"));
        assertEquals("false", fields.get(prefix + ".approvalBound"));
        assertEquals("false", fields.get(prefix + ".optimizedArtifactBuilt"));
        assertEquals("false", fields.get(prefix + ".transformedIrBuilt"));
        assertEquals("false", fields.get(prefix + ".mutationAllowed"));
        assertEquals("false", fields.get(prefix + ".selectedIrReplacement"));
    }

    private static void assertFailClosedSelectionFields(Map<String, String> fields, String prefix) {
        assertEquals("true", fields.get(prefix + ".selectionPreflightImplemented"));
        assertEquals("false", fields.get(prefix + ".proofBound"));
        assertEquals("false", fields.get(prefix + ".rollbackBound"));
        assertEquals("false", fields.get(prefix + ".approvalBound"));
        assertEquals("false", fields.get(prefix + ".optimizedArtifactBuilt"));
        assertEquals("false", fields.get(prefix + ".transformedIrBuilt"));
        assertEquals("false", fields.get(prefix + ".productionGateAccepted"));
        assertEquals("false", fields.get(prefix + ".mutationPolicyAllowed"));
        assertEquals("false", fields.get(prefix + ".selectionApplied"));
        assertEquals("false", fields.get(prefix + ".optimizedArtifactSelected"));
        assertEquals("false", fields.get(prefix + ".mutationAllowed"));
        assertEquals("false", fields.get(prefix + ".selectedIrReplacement"));
    }
}
