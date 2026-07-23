package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendContractReadinessExampleTest {

    @Test
    void rendersMetadataOnlyBackendContractDashboard() {
        String output = BackendContractReadinessExample.renderBackendContractReadiness();

        assertTrue(output.contains("Backend contract readiness dashboard:"), output);
        assertTrue(output.contains("metadata-only"), output);
        assertTrue(output.contains("OpenCL backend SPI contract:"), output);
        assertTrue(output.contains("- status=ready"), output);
        assertTrue(output.contains("- provider=backend-provider:opencl"), output);
        assertTrue(output.contains("- pipelineAvailable=true"), output);
        assertTrue(output.contains("Backend source/lowering contract:"), output);
        assertTrue(output.contains("- readyEntries=4/4"), output);
        assertTrue(output.contains("- plannedUnsupported=2/2"), output);
        assertTrue(output.contains("- previewLowering=1"), output);
        assertTrue(output.contains("- entry.OPENCL=status:ready,stage:SUCCEEDED,lowered:true"), output);
        assertTrue(output.contains("- entry.CUDA=status:ready,stage:SUCCEEDED,lowered:true"), output);
        assertTrue(output.contains("CUDA inventory contract:"), output);
        assertTrue(output.contains("- provider=backend-provider:cuda"), output);
        assertTrue(output.contains("- catalogProductionAdapter=false"), output);
        assertTrue(output.contains("- lowererSelectedSource=cuda-irgpu-source-unavailable"), output);
        assertTrue(output.contains("CUDA execution green-light checklist:"), output);
        assertTrue(output.contains("- cudaExecutionAvailability=execution-pipeline-available"), output);
        assertTrue(output.contains("- cudaPipelineAvailable=true"), output);
        assertTrue(output.contains("- cudaPipelineFactoryPresent=true"), output);
        assertTrue(output.contains("- unsupportedReceipt=compile:SUCCEEDED,prepare:UNSUPPORTED,invoke:SKIPPED"), output);
        assertTrue(output.contains("- checklistReady=9/9"), output);
        assertTrue(output.contains("- checklistBlocked=0"), output);
        assertTrue(output.contains("- checklist.cuda-vertical-slice-skeleton-present=ready"), output);
        assertTrue(output.contains("- checklist.cuda-native-bridge-fail-closed=ready"), output);
    }
}
