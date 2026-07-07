package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceParityComparison;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourceReconstructionResultTest {

    @Test
    void blockedResultRecordsFutureBackendSourceFailureWithoutSourcePayload() {
        GpuBackendSourceReconstructionResult result = GpuBackendSourceReconstructionResult.blocked(
                GpuBackendTarget.OPENCL,
                "derived-opencl-source",
                "ir-text-v1",
                "opencl-source-compile",
                List.of("typed-body-regeneration-not-yet-available"),
                List.of("runtime must use generated OpenCL fallback")
        );

        assertTrue(result.attempted());
        assertFalse(result.reconstructed());
        assertFalse(result.sourceAvailable());
        assertTrue(result.toLine().contains("selectedSource=derived-opencl-source"));
        assertTrue(result.toLine().contains("sourceAvailable=false"));
        assertTrue(result.toPropertiesText().contains("backendTarget=OPENCL"));
        assertTrue(result.toPropertiesText().contains("attempted=true"));
        assertTrue(result.toPropertiesText().contains("reconstructed=false"));
        assertTrue(result.toPropertiesText().contains("blocker.0=typed-body-regeneration-not-yet-available"));
    }

    @Test
    void reconstructedResultRecordsBackendSourcePayloadMetadata() {
        GpuBackendSourceReconstructionResult result = GpuBackendSourceReconstructionResult.reconstructedSource(
                GpuBackendTarget.OPENCL,
                "__kernel void jtg_kernel() {}",
                "irgpu-backend-neutral-source",
                "ir-text-v1",
                "opencl-irgpu-source-compile",
                List.of("source reconstructed from IrGpu preview")
        );

        assertTrue(result.attempted());
        assertTrue(result.reconstructed());
        assertTrue(result.sourceAvailable());
        assertTrue(result.toLine().contains("selectedSource=irgpu-backend-neutral-source"));
        assertTrue(result.toLine().contains("sourceAvailable=true"));
        assertTrue(result.toPropertiesText().contains("reconstructed=true"));
        assertTrue(result.toPropertiesText().contains("sourceOrigin=irgpu-backend-neutral-source"));
        assertTrue(result.toPropertiesText().contains("runtimeLoadMode=opencl-irgpu-source-compile"));
        assertTrue(result.toPropertiesText().contains("diagnostic.0=source reconstructed from IrGpu preview"));
    }

    @Test
    void sourceParityComparisonReportsNormalizedMatchAndMismatch() {
        OpenClIrGpuSourceParityComparison matched = OpenClIrGpuSourceParityComparison.compare(
                "__kernel void jtg_kernel() {\n    return;\n}\n",
                "__kernel   void   jtg_kernel() { return; }"
        );
        OpenClIrGpuSourceParityComparison mismatched = OpenClIrGpuSourceParityComparison.compare(
                "__kernel void jtg_kernel() {\n    return;\n}\n",
                "__kernel void jtg_kernel() {\n    return 1;\n}\n"
        );

        assertTrue(matched.checked());
        assertTrue(matched.matched());
        assertTrue(matched.diagnostics().contains("sourceParity.matched=true"));
        assertTrue(mismatched.checked());
        assertFalse(mismatched.matched());
        assertTrue(mismatched.diagnostics().contains("sourceParity.matched=false"));
    }

    @Test
    void backendSourcePromotionGateStaysBlockedWithoutParityAndRuntimeEvidence() {
        GpuBackendSourceReconstructionResult result = GpuBackendSourceReconstructionResult.reconstructedSource(
                GpuBackendTarget.OPENCL,
                "__kernel void jtg_kernel() {}",
                "irgpu-backend-neutral-source",
                "ir-text-v1",
                "opencl-irgpu-source-compile",
                List.of("sourceParity.checked=true", "sourceParity.matched=false")
        );

        GpuBackendSourcePromotionGate gate = GpuBackendSourcePromotionGate.evaluate(
                result,
                GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed"),
                GpuRuntimeFallbackEvidence.none()
        );

        assertFalse(gate.reviewReady());
        assertTrue(gate.toPropertiesText().contains("status=blocked"));
        assertTrue(gate.toPropertiesText().contains("sourceParityChecked=true"));
        assertTrue(gate.toPropertiesText().contains("sourceParityMatched=false"));
        assertTrue(gate.toPropertiesText().contains("runtimeEquivalencePassed=false"));
        assertTrue(gate.toPropertiesText().contains("diagnostic.0=reconstructed source must match descriptor source before promotion review"));
        assertTrue(gate.toPropertiesText().contains("reconstruction.blocker.count=0"));
        assertTrue(gate.toPropertiesText().contains("reconstruction.diagnostic.count=2"));
        assertTrue(gate.toPropertiesText().contains("reconstruction.diagnostic.0=sourceParity.checked=true"));
        assertTrue(gate.toPropertiesText().contains("reconstruction.diagnostic.1=sourceParity.matched=false"));
    }

    @Test
    void backendSourcePromotionGateExposesReconstructionBlockersForWorkloadDiagnostics() {
        GpuBackendSourceReconstructionResult result = GpuBackendSourceReconstructionResult.blocked(
                GpuBackendTarget.OPENCL,
                "descriptor-opencl-source",
                "unknown",
                "opencl-descriptor-source-compile",
                List.of("irgpu-artifact-missing"),
                List.of("OpenCL reconstruction preview skipped because no IrGpu artifact was available")
        );

        GpuBackendSourcePromotionGate gate = GpuBackendSourcePromotionGate.evaluate(
                result,
                GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed"),
                GpuRuntimeFallbackEvidence.none()
        );

        assertFalse(gate.reviewReady());
        assertTrue(gate.toPropertiesText().contains("reconstruction.blocker.count=1"));
        assertTrue(gate.toPropertiesText().contains("reconstruction.blocker.0=irgpu-artifact-missing"));
        assertTrue(gate.toPropertiesText().contains("reconstruction.diagnostic.count=1"));
        assertTrue(gate.toPropertiesText().contains("reconstruction.diagnostic.0=OpenCL reconstruction preview skipped because no IrGpu artifact was available"));
        assertTrue(gate.toPropertiesText().contains("diagnostic.0=backend source must be reconstructed from IrGpu before promotion review"));
    }

    @Test
    void backendSourcePromotionGateCanReachReviewReadyWithParityAndRuntimeEvidence() {
        GpuBackendSourceReconstructionResult result = GpuBackendSourceReconstructionResult.reconstructedSource(
                GpuBackendTarget.OPENCL,
                "__kernel void jtg_kernel() {}",
                "irgpu-backend-neutral-source",
                "ir-text-v1",
                "opencl-irgpu-source-compile",
                List.of("sourceParity.checked=true", "sourceParity.matched=true")
        );

        GpuBackendSourcePromotionGate gate = GpuBackendSourcePromotionGate.evaluate(
                result,
                GpuRuntimeEquivalenceEvidence.passed(null, 2, 2, List.of("source parity runtime equivalence passed")),
                GpuRuntimeFallbackEvidence.none()
        );

        assertTrue(gate.reviewReady());
        assertTrue(gate.toPropertiesText().contains("status=review-ready"));
        assertTrue(gate.toPropertiesText().contains("sourceParityMatched=true"));
        assertTrue(gate.toPropertiesText().contains("runtimeEquivalencePassed=true"));
        assertTrue(gate.toPropertiesText().contains("diagnostic.0=backend source reconstruction is ready for promotion review"));
    }
}
