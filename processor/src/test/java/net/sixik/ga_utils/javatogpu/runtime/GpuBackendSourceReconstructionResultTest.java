package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
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
}
