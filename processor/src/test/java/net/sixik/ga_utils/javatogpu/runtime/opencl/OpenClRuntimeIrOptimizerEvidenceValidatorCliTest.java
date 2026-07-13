package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenClRuntimeIrOptimizerEvidenceValidatorCliTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsFailClosedReviewPackageEvidenceFile() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "preview-readiness-blocked-by-proof", "1", "1"));

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                new String[]{artifact.toString()}
        ));
    }

    @Test
    void acceptsArtifactDirectory() throws Exception {
        Path artifactDirectory = temporaryDirectory.resolve("runtime-compile-artifacts").resolve("kernel-a");
        Files.createDirectories(artifactDirectory);
        Files.writeString(
                artifactDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT),
                failClosedEvidence("not-required", "false", "review-package-not-required", "0", "0")
        );

        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                new String[]{temporaryDirectory.toString()}
        ));
    }

    @Test
    void rejectsMissingArtifact() {
        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                        new String[]{temporaryDirectory.resolve("missing").toString()}
                )
        );
    }

    @Test
    void rejectsProductionMutationGuardrailRegression() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("reviewPackage.productionMutation=disabled", "reviewPackage.productionMutation=enabled"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsCompletedReviewPackage() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("reviewPackage.complete=false", "reviewPackage.complete=true"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsOptimizedArtifactCandidateSelectionRegression() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("optimizedArtifactCandidate.selectionApplied=false",
                        "optimizedArtifactCandidate.selectionApplied=" + Boolean.TRUE));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsOptimizedArtifactCandidateSelectedIrReplacementRegression() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true",
                "manual-review-required", "1", "0")
                .replace("optimizedArtifactCandidate.selectedIrReplacement.count=0",
                        "optimizedArtifactCandidate.selectedIrReplacement.count=1"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsRequiredPackageWithoutBlocker() throws Exception {
        Path artifact = temporaryDirectory.resolve(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        Files.writeString(artifact, failClosedEvidence("pending-manual-review", "true", "none", "1", "0"));

        assertThrows(
                IllegalStateException.class,
                () -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    private static String failClosedEvidence(
            String status,
            String required,
            String firstBlocker,
            String proposalPassCount,
            String pendingApprovalCount
    ) {
        return String.join("\n",
                "status=recorded",
                "runtimeEquivalenceReview.productionMutation=disabled",
                "runtimeEquivalenceReview.selectedIrReplacement=disabled",
                "runtimeEquivalenceReview.manualReviewOnly=true",
                "optimizedArtifactCandidate.status=not-recorded",
                "optimizedArtifactCandidate.count=0",
                "optimizedArtifactCandidate.ready.count=0",
                "optimizedArtifactCandidate.blocked.count=0",
                "optimizedArtifactCandidate.selectionReady.count=0",
                "optimizedArtifactCandidate.selectionApplied.count=0",
                "optimizedArtifactCandidate.selectedIrReplacement.count=0",
                "optimizedArtifactCandidate.mutationAllowed.count=0",
                "optimizedArtifactCandidate.firstBlocker=no-candidates",
                "optimizedArtifactCandidate.selectionFirstBlocker=no-candidates",
                "optimizedArtifactCandidate.selectionApplied=false",
                "optimizedArtifactCandidate.selectedIrReplacement=false",
                "reviewPackage.status=" + status,
                "reviewPackage.required=" + required,
                "reviewPackage.complete=false",
                "reviewPackage.firstBlocker=" + firstBlocker,
                "reviewPackage.proposalPass.count=" + proposalPassCount,
                "reviewPackage.pendingApproval.count=" + pendingApprovalCount,
                "reviewPackage.runtimeEquivalence.status=blocked",
                "reviewPackage.originalIrRequired=true",
                "reviewPackage.optimizedIrRequired=true",
                "reviewPackage.proofSummaryRequired=true",
                "reviewPackage.manualReviewOnly=true",
                "reviewPackage.productionMutation=disabled",
                "reviewPackage.selectedIrReplacement=disabled",
                ""
        );
    }
}
